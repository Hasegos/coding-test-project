package io.dev.coding_test.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LM Studio 클라이언트 (OpenAI 호환 {@code POST /v1/chat/completions}).
 * <p>
 * {@code response_format.type = json_schema}로 구조화 출력을 강제한다.
 * OpenAI 호환 API를 제공하는 다른 서버(vLLM, llama.cpp server 등)에도 그대로 사용할 수 있다.
 * </p>
 */
public class LmStudioLlmClient extends AbstractLlmClient {

    private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                    "name", "memo_summary",
                    "strict", true,
                    "schema", SummaryPrompt.SCHEMA
            )
    );

    public LmStudioLlmClient(RestClient restClient, LlmProperties properties, SummaryResultParser parser) {
        super(restClient, properties, parser);
    }

    @Override
    protected String requestCompletion(String system, String user) {
        ChatRequest request = new ChatRequest(
                properties.model(),
                List.of(new Message("system", system), new Message("user", user)),
                properties.temperature(),
                false,
                RESPONSE_FORMAT
        );

        ChatResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new LlmException("LM Studio 응답 형식이 올바르지 않아요.");
        }
        return response.choices().getFirst().message().content();
    }

    record ChatRequest(String model,
                       List<Message> messages,
                       double temperature,
                       boolean stream,
                       @JsonProperty("response_format") Map<String, Object> responseFormat) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }
}
