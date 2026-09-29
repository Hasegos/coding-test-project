package io.dev.coding_test.llm.parser;

import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 응답 문자열을 {@link SummaryResult}로 해석하는 파서.
 * <p>
 * 로컬 모델은 지시를 완벽히 따르지 않는 경우가 있어 다음을 보정한다.
 * </p>
 * <ul>
 *     <li>추론 모델의 {@code <think>...</think>} 블록, 마크다운 코드 블록 제거</li>
 *     <li>JSON 앞뒤에 붙은 설명 문장 무시 (첫 '{' ~ 마지막 '}'만 사용)</li>
 *     <li>할 일 항목의 공백·중복·빈 값 제거, 개수·길이 제한</li>
 *     <li>할 일이 문자열 대신 객체({"task": "..."})로 온 경우 첫 문자열 값 사용</li>
 *     <li>메모에 없는 담당자·기한이 붙은 할 일 제거 ({@link #parse(String, String)})
 *         — 모델이 프롬프트의 예시나 지어낸 이름을 옮겨 적은 항목</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryResultParser {

    public static final int MAX_SUMMARY_LENGTH = 5_000;
    public static final int MAX_TODO_COUNT = 20;
    public static final int MAX_TODO_LENGTH = 500;

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    /** 할 일 앞의 [담당자, 기한] 태그 */
    private static final Pattern TODO_TAG = Pattern.compile("^\\[([^\\]]{1,60})]");
    /** 모델이 덧붙이는 조사·호칭 (예: "금요일까지", "지훈님") */
    private static final Pattern TAG_SUFFIX = Pattern.compile("(까지|님|씨)$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private final JsonMapper jsonMapper;

    /**
     * LLM 응답 문자열을 요약 결과로 변환한다.
     *
     * @param raw LLM이 생성한 응답 본문
     * @return 요약 결과
     * @throws LlmException JSON을 찾을 수 없거나 요약문이 비어 있는 경우
     */
    public SummaryResult parse(String raw) {
        return parse(raw, null);
    }

    /**
     * LLM 응답 문자열을 요약 결과로 변환하고, 원문에 없는 담당자·기한이 붙은 할 일을 제거한다.
     * <p>
     * 숫자가 들어간 태그(예: "10/14까지")는 모델이 날짜 표기를 바꿔 쓰는 경우가 많아 검사하지 않고,
     * 이름·요일 같은 글자 태그만 원문(공백 무시)에 있는지 확인한다.
     * </p>
     *
     * @param raw    LLM이 생성한 응답 본문
     * @param source 요약한 메모 원문 (제목 + 본문), {@code null}이면 검사하지 않는다
     * @return 요약 결과
     * @throws LlmException JSON을 찾을 수 없거나 요약문이 비어 있는 경우
     */
    public SummaryResult parse(String raw, String source) {
        if (raw == null || raw.isBlank()) {
            throw new LlmException("LLM 응답이 비어 있어요.");
        }

        String text = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("LLM 응답에서 JSON을 찾지 못했어요.");
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(text.substring(start, end + 1));
        } catch (JacksonException e) {
            throw new LlmException("LLM 응답 JSON을 해석하지 못했어요.", e);
        }

        JsonNode summaryNode = root.path("summary");
        String summary = summaryNode.isString() ? summaryNode.asString().strip() : "";
        if (summary.isEmpty()) {
            throw new LlmException("LLM 응답에 요약(summary)이 없어요.");
        }
        return new SummaryResult(truncate(summary, MAX_SUMMARY_LENGTH), parseTodos(root.path("todos"), source));
    }

    private List<String> parseTodos(JsonNode node, String source) {
        if (!node.isArray()) {
            return List.of();
        }
        String compactSource = source == null ? null : compact(source);
        Set<String> todos = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String todo = todoText(item).strip().replaceAll("\\s+", " ");
            if (compactSource != null && !isGrounded(todo, compactSource)) {
                log.info("원문에 없는 담당자·기한이 붙은 할 일 제외 - {}", todo);
                continue;
            }
            if (!todo.isEmpty()) {
                todos.add(truncate(todo, MAX_TODO_LENGTH));
            }
            if (todos.size() == MAX_TODO_COUNT) {
                break;
            }
        }
        return new ArrayList<>(todos);
    }

    /**
     * 할 일의 [담당자, 기한] 태그 중 숫자가 없는 값이 모두 원문에 있으면 {@code true}. 태그가 없으면 {@code true}.
     */
    private static boolean isGrounded(String todo, String compactSource) {
        Matcher matcher = TODO_TAG.matcher(todo);
        if (!matcher.find()) {
            return true;
        }
        for (String part : matcher.group(1).split("[,/·]")) {
            String value = TAG_SUFFIX.matcher(compact(part)).replaceAll("");
            if (value.isEmpty() || DIGIT.matcher(value).find()) {
                continue;
            }
            if (!compactSource.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static String compact(String value) {
        return WHITESPACE.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private String todoText(JsonNode item) {
        if (item.isString()) {
            return item.asString();
        }
        if (item.isObject()) {
            for (JsonNode value : item.values()) {
                if (value.isString()) {
                    return value.asString();
                }
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
