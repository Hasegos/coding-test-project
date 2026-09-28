package io.dev.coding_test.llm.provider.lmstudio;

import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.model.enums.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LmStudioLlmClientTest {

    private static final String BASE_URL = "http://lmstudio.test";

    private final LlmProperties properties = new LlmProperties(0.2, null, null, 1, 10);
    private final LlmConnection connection =
            new LlmConnection(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "qwen2.5-7b-instruct", null);

    private MockRestServiceServer server;
    private LmStudioLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LmStudioLlmClient(builder.build(), connection, properties,
                new SummaryResultParser(JsonMapper.builder().build()));
    }

    @Test
    void OpenAI_호환_API로_response_format과_함께_요청하고_응답을_해석한다() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5-7b-instruct"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("memo_summary"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.todos.type").value("array"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("제목: 스터디")))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-1",
                          "object": "chat.completion",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "{\\"summary\\": \\"JPA N+1 문제를 정리했다.\\", \\"todos\\": [\\"fetch join 예제 작성하기\\", \\"batch size 설정 비교하기\\"]}"
                            },
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 100, "completion_tokens": 40}
                        }
                        """, MediaType.APPLICATION_JSON));

        SummaryResult result = client.summarize("스터디", "N+1 문제");

        assertThat(result.summary()).isEqualTo("JPA N+1 문제를 정리했다.");
        assertThat(result.todos()).containsExactly("fetch join 예제 작성하기", "batch size 설정 비교하기");
        server.verify();
    }

    @Test
    void 서버_오류_응답은_LlmException으로_변환한다() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"No models loaded\"}"));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("No models loaded");
    }

    @Test
    void choices가_비어있으면_LlmException이_발생한다() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("LM Studio 응답 형식");
    }

    @Test
    void 모델_목록을_조회하고_중복_제거_후_정렬한다() {
        server.expect(requestTo(BASE_URL + "/v1/models"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"object": "list", "data": [{"id": "qwen2.5-vl-7b-instruct", "object": "model"}, {"id": "text-embedding-nomic-embed-text-v1.5"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listModels()).containsExactly("qwen2.5-vl-7b-instruct", "text-embedding-nomic-embed-text-v1.5");
        server.verify();
    }

    @Test
    void 모델_목록_조회_실패는_LlmException으로_변환한다() {
        server.expect(requestTo(BASE_URL + "/v1/models"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("unauthorized"));

        assertThatThrownBy(() -> client.listModels())
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 401");
    }
}
