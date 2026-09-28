package io.dev.coding_test.llm.provider.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Ollama {@code GET /api/tags} 응답 본문 (설치된 모델 목록, 필요한 필드만 매핑).
 *
 * @param models 설치된 모델 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaTagsResponse(List<Model> models) {

    /**
     * 설치된 모델.
     *
     * @param name 모델명 (예: qwen2.5:7b)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String name) {
    }
}
