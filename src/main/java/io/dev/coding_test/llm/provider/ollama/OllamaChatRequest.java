package io.dev.coding_test.llm.provider.ollama;

import io.dev.coding_test.llm.dto.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * Ollama {@code POST /api/chat} 요청 본문.
 *
 * @param model    모델명
 * @param messages 대화 메시지
 * @param stream   스트리밍 여부 (항상 false — 응답을 한 번에 받음)
 * @param format   구조화 출력 JSON 스키마
 * @param options  생성 옵션 (temperature 등)
 */
public record OllamaChatRequest(String model,
                                List<ChatMessage> messages,
                                boolean stream,
                                Object format,
                                Map<String, Object> options) {
}
