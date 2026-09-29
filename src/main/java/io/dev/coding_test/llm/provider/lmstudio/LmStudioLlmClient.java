package io.dev.coding_test.llm.provider.lmstudio;

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
 * LM Studio 클라이언트 (OpenAI 호환 요약 {@code POST /v1/chat/completions}, 모델 목록 {@code GET /v1/models}).
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

    public LmStudioLlmClient(RestClient chatClient, RestClient modelsClient, LlmConnection connection,
                             LlmProperties properties, SummaryResultParser parser) {
        super(chatClient, modelsClient, connection, properties, parser);
    }

    @Override
    protected String requestCompletion(String system, String user) {
        LmStudioChatRequest request = new LmStudioChatRequest(
                connection.model(),
                List.of(ChatMessage.system(system), ChatMessage.user(user)),
                properties.temperature(),
                false,
                RESPONSE_FORMAT
        );

        LmStudioChatResponse response = postJson("/v1/chat/completions", request, LmStudioChatResponse.class);

        if (response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new LlmException("LM Studio 응답 형식이 올바르지 않아요.");
        }
        return response.choices().getFirst().message().content();
    }

    @Override
    protected Optional<List<String>> requestModels() {
        return getJson("/v1/models", LmStudioModelsResponse.class).map(response -> {
            if (response.data() == null) {
                throw new LlmException("LM Studio 모델 목록 응답 형식이 올바르지 않아요.");
            }
            return response.data().stream().map(LmStudioModelsResponse.Model::id).toList();
        });
    }
}
