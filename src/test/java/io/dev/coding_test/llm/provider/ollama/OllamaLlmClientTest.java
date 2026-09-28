package io.dev.coding_test.llm.provider.ollama;

import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.model.enums.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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

    private final LlmProperties properties = new LlmProperties(0.2, null, null, null, null, 1, 10);
    private final LlmConnection connection =
            new LlmConnection(LlmProvider.OLLAMA, "192.168.0.10", 11434, "qwen2.5:7b", null);

    private MockRestServiceServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaLlmClient(builder.build(), builder.build(), connection, properties,
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
    void 메모에_없는_담당자가_붙은_할_일은_결과에서_빠진다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withSuccess("""
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"summary\\": \\"QA 일정을 논의했다.\\", \\"todos\\": [\\"[민수, 10/14까지] 배포 스크립트 점검하기\\", \\"[지영] QA 일정 공유하기\\"]}"
                          },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        SummaryResult result = client.summarize("주간 회의", "지영: QA 일정 공유 필요");

        assertThat(result.todos()).containsExactly("[지영] QA 일정 공유하기");
    }

    @Test
    void 채팅_404는_모델을_찾을_수_없다는_메시지로_변환한다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model 'qwen2.5:7b' not found\"}"));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM 서버에서 모델 'qwen2.5:7b'을(를) 찾을 수 없어요. LLM 설정에서 모델을 다시 선택해주세요.");
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
    void 모델_목록을_조회하고_중복_제거_후_정렬하며_임베딩_모델은_뺀다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"models": [{"name": "qwen2.5:7b", "size": 4683087332}, {"name": "llama3.2:3b"},
                                    {"name": "qwen2.5:7b"}, {"name": "nomic-embed-text:latest"}, {"name": "bge-m3:latest"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listModels()).hasValue(List.of("llama3.2:3b", "qwen2.5:7b"));
        server.verify();
    }

    @Test
    void 모델_목록_API가_404면_미지원으로_본다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.listModels()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "401 | LLM 서버 인증에 실패했어요. API Key를 확인해주세요.",
            "403 | LLM 서버 인증에 실패했어요. API Key를 확인해주세요.",
            "429 | LLM 서버 요청 한도를 초과했어요. 잠시 후 다시 시도해주세요.",
            "302 | LLM 서버가 다른 주소로 리다이렉트했어요. IP·포트를 확인해주세요.",
            "500 | LLM 서버가 500 응답을 반환했어요.",
    })
    void 상태_코드를_사용자용_메시지로_변환한다(int status, String message) {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("unauthorized"));

        assertThatThrownBy(() -> client.listModels())
                .isInstanceOf(LlmException.class)
                .hasMessage(message);
    }

    @Test
    void 오류_응답_본문은_JSON이어도_노출하지_않는다() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"message\": \"Internal Admin Panel v1.2 - secret-token=abc\"}}"));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM 서버가 요청을 거부했어요. (400) 모델이 로드되어 있는지, 모델명이 맞는지 확인해주세요.")
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void JSON이_아닌_성공_응답은_응답_형식_오류로_변환한다() {
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withSuccess("<html><body>Router admin</body></html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.listModels())
                .isInstanceOf(LlmException.class)
                .hasMessageStartingWith("Ollama 응답 형식이 올바르지 않아요.")
                .hasMessageNotContaining("Router admin");
    }

    @Test
    void 최대_크기를_넘는_응답은_읽기를_중단한다() {
        LlmProperties small = new LlmProperties(0.2, null, null, null, DataSize.ofKilobytes(1), 1, 10);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer smallServer = MockRestServiceServer.bindTo(builder).build();
        OllamaLlmClient smallClient = new OllamaLlmClient(builder.build(), builder.build(), connection, small,
                new SummaryResultParser(JsonMapper.builder().build()));
        smallServer.expect(requestTo(BASE_URL + "/api/tags"))
                .andRespond(withSuccess("{\"models\": [{\"name\": \"" + "a".repeat(2048) + "\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(smallClient::listModels)
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM 응답이 너무 커요. (최대 1KB)");
    }
}
