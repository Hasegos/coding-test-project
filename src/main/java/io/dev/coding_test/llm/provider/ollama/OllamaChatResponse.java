package io.dev.coding_test.llm.provider.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.dev.coding_test.llm.dto.ChatMessage;

/**
 * Ollama {@code POST /api/chat} 응답 본문 (필요한 필드만 매핑).
 *
 * @param message 모델이 생성한 메시지
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(ChatMessage message) {
}
