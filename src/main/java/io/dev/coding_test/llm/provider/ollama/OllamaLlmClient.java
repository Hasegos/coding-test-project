package io.dev.coding_test.llm.provider.ollama;

import io.dev.coding_test.llm.client.AbstractLlmClient;
import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.ChatMessage;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.prompt.SummaryPrompt;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ollama 클라이언트 (요약 {@code POST /api/chat}, 모델 목록 {@code GET /api/tags}).
 * <p>
 * {@code format}에 JSON 스키마를 전달해 구조화 출력을 강제하고, {@code stream: false}로 응답을 한 번에 받는다.
 * </p>
 */
public class OllamaLlmClient extends AbstractLlmClient {

    public OllamaLlmClient(RestClient chatClient, RestClient modelsClient, LlmConnection connection,
                           LlmProperties properties, SummaryResultParser parser) {
        super(chatClient, modelsClient, connection, properties, parser);
    }

    @Override
    protected String requestCompletion(String system, String user) {
        OllamaChatRequest request = new OllamaChatRequest(
                connection.model(),
                List.of(ChatMessage.system(system), ChatMessage.user(user)),
                false,
                SummaryPrompt.SCHEMA,
                Map.of("temperature", properties.temperature())
        );

        OllamaChatResponse response = postJson("/api/chat", request, OllamaChatResponse.class);

        if (response.message() == null) {
            throw new LlmException("Ollama 응답 형식이 올바르지 않아요.");
        }
        return response.message().content();
    }

    @Override
    protected Optional<List<String>> requestModels() {
        return getJson("/api/tags", OllamaTagsResponse.class).map(response -> {
            if (response.models() == null) {
                throw new LlmException("Ollama 모델 목록 응답 형식이 올바르지 않아요.");
            }
            return response.models().stream().map(OllamaTagsResponse.Model::name).toList();
        });
    }
}
