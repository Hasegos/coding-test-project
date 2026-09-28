package io.dev.coding_test.llm.provider.lmstudio;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dev.coding_test.llm.dto.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * LM Studio(OpenAI 호환) {@code POST /v1/chat/completions} 요청 본문.
 *
 * @param model          모델명
 * @param messages       대화 메시지
 * @param temperature    생성 온도
 * @param stream         스트리밍 여부 (항상 false — 응답을 한 번에 받음)
 * @param responseFormat 구조화 출력 형식 ({@code json_schema})
 */
public record LmStudioChatRequest(String model,
                                  List<ChatMessage> messages,
                                  double temperature,
                                  boolean stream,
                                  @JsonProperty("response_format") Map<String, Object> responseFormat) {
}
