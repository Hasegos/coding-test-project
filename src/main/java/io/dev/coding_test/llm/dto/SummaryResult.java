package io.dev.coding_test.llm.dto;

import java.util.List;

/**
 * 로컬 LLM 요약 결과.
 *
 * @param summary 메모 요약문
 * @param todos   메모에서 추출한 할 일 목록, 없으면 빈 리스트
 */
public record SummaryResult(String summary, List<String> todos) {

    public SummaryResult {
        todos = todos == null ? List.of() : List.copyOf(todos);
    }
}
