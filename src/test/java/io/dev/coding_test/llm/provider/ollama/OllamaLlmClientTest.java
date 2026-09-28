package io.dev.coding_test.llm.provider.ollama;

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

class OllamaLlmClientTest {

    private static final String BASE_URL = "http://ollama.test";

    private final LlmProperties properties = new LlmProperties(0.2, null, null, 1, 10);
    private final LlmConnection connection =
            new LlmConnection(LlmProvider.OLLAMA, "127.0.0.1", 11434, "qwen2.5:7b", null);

    private MockRestServiceServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaLlmClient(builder.build(), connection, properties,
                new SummaryResultParser(JsonMapper.builder().build()));
    }

    @Test
    void api_chat으로_JSON_스키마와_함께_요청하고_응답을_해석한다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format.type").value("object"))
                .andExpect(jsonPath("$.format.required[0]").value("summary"))
                .andExpect(jsonPath("$.options.temperature").value(0.2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("제목: 주간 회의")))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("배포 일정 논의")))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen2.5:7b",
                          "message": {
                            "role": "assistant",
                            "content": "{\\"summary\\": \\"배포 일정을 논의했다.\\", \\"todos\\": [\\"배포 스크립트 점검하기\\"]}"
                          },
                          "done": true,
                          "total_duration": 1234
                        }
                        """, MediaType.APPLICATION_JSON));

        SummaryResult result = client.summarize("주간 회의", "배포 일정 논의");

        assertThat(result.summary()).isEqualTo("배포 일정을 논의했다.");
        assertThat(result.todos()).containsExactly("배포 스크립트 점검하기");
        assertThat(client.model()).isEqualTo("qwen2.5:7b");
        server.verify();
    }

    @Test
    void 서버_오류_응답은_상태코드와_본문을_담은_LlmException으로_변환한다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model 'qwen2.5:7b' not found\"}"));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining("not found");
    }

    @Test
    void 응답에_message가_없으면_LlmException이_발생한다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withSuccess("{\"done\": true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Ollama 응답 형식");
    }

    @Test
    void 모델_목록을_조회하고_중복_제거_후_정렬한다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"models": [{"name": "qwen2.5:7b", "size": 4683087332}, {"name": "llama3.2:3b"}, {"name": "qwen2.5:7b"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listModels()).containsExactly("llama3.2:3b", "qwen2.5:7b");
        server.verify();
    }

    @Test
    void 모델_목록_조회_실패는_LlmException으로_변환한다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("unauthorized"));

        assertThatThrownBy(() -> client.listModels())
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void JSON이_아닌_오류_응답_본문은_노출하지_않는다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Internal Admin Panel v1.2 - secret-token=abc</body></html>"));

        assertThatThrownBy(() -> client.listModels())
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM 서버 오류 (HTTP 404)");
    }

    @Test
    void JSON_오류는_error_message_필드만_보여준다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"message\": \"context length exceeded\", \"internal\": \"x\"}}"));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM 서버 오류 (HTTP 400): context length exceeded");
    }
}
