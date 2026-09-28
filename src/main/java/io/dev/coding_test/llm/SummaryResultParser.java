package io.dev.coding_test.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SummaryResultParser {

    public static final int MAX_SUMMARY_LENGTH = 5_000;
    public static final int MAX_TODO_COUNT = 20;
    public static final int MAX_TODO_LENGTH = 500;

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private final JsonMapper jsonMapper;

    /**
     * LLM 응답 문자열을 요약 결과로 변환한다.
     *
     * @param raw LLM이 생성한 응답 본문
     * @return 요약 결과
     * @throws LlmException JSON을 찾을 수 없거나 요약문이 비어 있는 경우
     */
    public SummaryResult parse(String raw) {
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
        return new SummaryResult(truncate(summary, MAX_SUMMARY_LENGTH), parseTodos(root.path("todos")));
    }

    private List<String> parseTodos(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<String> todos = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String todo = todoText(item).strip().replaceAll("\\s+", " ");
            if (!todo.isEmpty()) {
                todos.add(truncate(todo, MAX_TODO_LENGTH));
            }
            if (todos.size() == MAX_TODO_COUNT) {
                break;
            }
        }
        return new ArrayList<>(todos);
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
