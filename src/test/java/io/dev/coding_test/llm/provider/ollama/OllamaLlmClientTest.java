package io.dev.coding_test.llm.provider.ollama;

import io.dev.coding_test.llm.config.LlmProperties;
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
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaLlmClientTest {

    private static final String BASE_URL = "http://ollama.test";

    private final LlmProperties properties =
            new LlmProperties(LlmProvider.OLLAMA, BASE_URL, "qwen2.5:7b", "", 0.2, null, null, 1, 10);

    private MockRestServiceServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaLlmClient(builder.build(), properties,
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
}
