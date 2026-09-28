package io.dev.coding_test.llm;

import java.util.List;
import java.util.Map;

/**
 * 메모 요약 프롬프트와 응답 JSON 스키마.
 * <p>
 * 두 런타임 모두 JSON 스키마 기반 구조화 출력(Ollama {@code format}, LM Studio {@code response_format})을
 * 지원하므로 같은 스키마를 사용한다. 스키마를 지원하지 않는 모델이 설명 문장을 섞어 답해도
 * {@link SummaryResultParser}가 JSON 부분만 추출한다.
 * </p>
 */
public final class SummaryPrompt {

    private SummaryPrompt() {
    }

    public static final String SYSTEM = """
            너는 메모 정리 도우미다. 사용자가 준 메모를 읽고 반드시 아래 JSON 형식으로만 답한다.
            {"summary": "요약문", "todos": ["할 일 1", "할 일 2"]}

            규칙:
            1. summary: 메모의 핵심 내용을 한국어 3~5문장 이내로 요약한다.
            2. todos: 메모에서 실제로 해야 할 행동 항목만 추출한다. 각 항목은 "~하기"로 끝나는 짧은 한 문장으로 쓴다.
               담당자·기한이 적혀 있으면 항목 앞에 포함한다. (예: "[민수, 10/14까지] 배포 스크립트 점검하기")
            3. 할 일이 없으면 todos는 빈 배열 []로 둔다.
            4. 메모에 없는 내용은 추측해서 만들지 않는다.
            5. JSON 외의 설명, 마크다운, 코드 블록은 출력하지 않는다.
            """;

    /** 응답 JSON 스키마 ({"summary": string, "todos": string[]}) */
    public static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "todos", Map.of("type", "array", "items", Map.of("type", "string"))
            ),
            "required", List.of("summary", "todos"),
            "additionalProperties", false
    );

    /**
     * 사용자 메시지를 만든다.
     *
     * @param title   메모 제목
     * @param content 메모 본문
     * @return LLM에 전달할 사용자 메시지
     */
    public static String userMessage(String title, String content) {
        return "제목: " + title + "\n\n본문:\n" + content;
    }
}
