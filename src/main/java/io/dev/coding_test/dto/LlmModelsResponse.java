package io.dev.coding_test.dto;

import java.util.List;

/**
 * 연결 테스트(모델 목록 조회) 결과.
 *
 * @param models LLM 서버에서 사용 가능한 모델명 목록
 */
public record LlmModelsResponse(List<String> models) {
}
