package io.dev.coding_test.llm.provider.lmstudio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LM Studio(OpenAI 호환) {@code GET /v1/models} 응답 본문 (필요한 필드만 매핑).
 *
 * @param data 사용 가능한 모델 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LmStudioModelsResponse(List<Model> data) {

    /**
     * 사용 가능한 모델.
     *
     * @param id 모델 ID (예: qwen2.5-7b-instruct)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String id) {
    }
}
