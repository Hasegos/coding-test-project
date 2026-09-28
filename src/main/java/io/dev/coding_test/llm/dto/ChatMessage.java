package io.dev.coding_test.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM 채팅 메시지 (Ollama / OpenAI 호환 API 공통 형식).
 *
 * @param role    메시지 역할 (system, user, assistant)
 * @param content 메시지 본문
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
