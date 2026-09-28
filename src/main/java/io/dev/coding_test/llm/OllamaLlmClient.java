package io.dev.coding_test.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Ollama 클라이언트 ({@code POST /api/chat}).
 * <p>
 * {@code format}에 JSON 스키마를 전달해 구조화 출력을 강제하고, {@code stream: false}로 응답을 한 번에 받는다.
 * </p>
 */
public class OllamaLlmClient extends AbstractLlmClient {

    public OllamaLlmClient(RestClient restClient, LlmProperties properties, SummaryResultParser parser) {
        super(restClient, properties, parser);
    }

    @Override
    protected String requestCompletion(String system, String user) {
        ChatRequest request = new ChatRequest(
                properties.model(),
                List.of(new Message("system", system), new Message("user", user)),
                false,
                SummaryPrompt.SCHEMA,
                Map.of("temperature", properties.temperature())
        );

        ChatResponse response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.message() == null) {
            throw new LlmException("Ollama 응답 형식이 올바르지 않아요.");
        }
        return response.message().content();
    }

    record ChatRequest(String model,
                       List<Message> messages,
                       boolean stream,
                       Object format,
                       Map<String, Object> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(Message message) {
    }
}
