package io.dev.coding_test.controller;

import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.support.FakeLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
    }

    @Test
    void 설정하지_않았으면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/settings/llm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 접속_설정을_저장하고_API_Key_값은_응답하지_않는다() throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "LMSTUDIO", "host": "100.66.180.73", "port": 1234,
                                 "model": "qwen2.5-vl-7b-instruct", "apiKey": "secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("LMSTUDIO"))
                .andExpect(jsonPath("$.host").value("100.66.180.73"))
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(get("/api/settings/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("qwen2.5-vl-7b-instruct"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "169.254.169.254", "0.0.0.0", "localhost", "evil.example.com", "010.0.0.1"})
    void 로컬_IP가_아니면_400으로_거부한다(String host) throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "%s", "port": 11434, "model": "qwen2.5:7b"}
                                """.formatted(host)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("host"));
    }

    @Test
    void 포트_범위와_모델명_형식을_검증한다() throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "127.0.0.1", "port": 70000, "model": "bad model<script>"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void 연결_테스트는_모델_목록을_반환한다() throws Exception {
        fakeLlmClient.willListModels(() -> List.of("llama3.2:3b", "qwen2.5:7b"));

        mockMvc.perform(post("/api/settings/llm/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value("llama3.2:3b"))
                .andExpect(jsonPath("$.models[1]").value("qwen2.5:7b"));
    }

    @Test
    void 연결_테스트_실패는_502와_원인_메시지를_반환한다() throws Exception {
        fakeLlmClient.willListModels(() -> {
            throw new LlmException("로컬 LLM 서버(http://192.168.0.10:11434)에 연결할 수 없어요.");
        });

        mockMvc.perform(post("/api/settings/llm/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("로컬 LLM 서버(http://192.168.0.10:11434)에 연결할 수 없어요."));
    }

    @Test
    void 연결_테스트도_로컬_IP만_허용한다() throws Exception {
        mockMvc.perform(post("/api/settings/llm/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "169.254.169.254", "port": 80}
                                """))
                .andExpect(status().isBadRequest());
    }
}
