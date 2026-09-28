package io.dev.coding_test.llm.provider.lmstudio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.dev.coding_test.llm.dto.ChatMessage;

import java.util.List;

/**
 * LM Studio(OpenAI 호환) {@code POST /v1/chat/completions} 응답 본문 (필요한 필드만 매핑).
 *
 * @param choices 생성 결과 후보 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LmStudioChatResponse(List<Choice> choices) {

    /**
     * 생성 결과 후보.
     *
     * @param message 모델이 생성한 메시지
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message) {
    }
}
