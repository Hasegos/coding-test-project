package io.dev.coding_test.controller.api;

import io.dev.coding_test.dto.setting.LlmConnectionTestResponse;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import io.dev.coding_test.support.FakeLlmClient;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
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
    private TestUsers testUsers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long userId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @BeforeEach
    void setUp() {
        userId = testUsers.login("tester").getUserId();
        fakeLlmClient.reset();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
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
    @ValueSource(strings = {"8.8.8.8", "169.254.169.254", "0.0.0.0", "127.0.0.1", "localhost", "host.docker.internal",
            "evil.example.com", "010.0.0.1", "127.1", "2130706433", "::1", "::ffff:127.0.0.1", "fe80::1", "192.0.2.1", "224.0.0.1"})
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
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 70000, "model": "bad model<script>"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void 거부_사유에_맞는_안내_메시지를_반환한다() throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "127.0.0.1", "port": 11434, "model": "qwen2.5:7b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(LlmHostGuard.LOCALHOST_MESSAGE));

        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "169.254.169.254", "port": 11434, "model": "qwen2.5:7b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(LlmHostGuard.BLOCKED_MESSAGE));

        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "LMSTUDIO", "host": "http://100.66.180.73", "port": 1234, "model": "qwen2.5:7b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(LlmHostGuard.URL_MESSAGE));
    }

    @Test
    void IPv6_사설_주소_ULA도_저장할_수_있다() throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "fd7a:115c:a1e0::1", "port": 11434, "model": "qwen2.5:7b"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("fd7a:115c:a1e0::1"));
    }

    @Test
    void 연결_테스트는_모델_목록을_반환한다() throws Exception {
        fakeLlmClient.willListModels(() -> Optional.of(List.of("llama3.2:3b", "qwen2.5:7b")));

        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.latencyMs").isNumber())
                .andExpect(jsonPath("$.models[0]").value("llama3.2:3b"))
                .andExpect(jsonPath("$.models[1]").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void 모델_목록을_지원하지_않거나_비어_있으면_안내_메시지를_담는다() throws Exception {
        fakeLlmClient.willListModels(Optional::empty);

        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.models").doesNotExist())
                .andExpect(jsonPath("$.message").value(LlmConnectionTestResponse.MODELS_UNSUPPORTED_MESSAGE));

        fakeLlmClient.willListModels(() -> Optional.of(List.of()));

        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.models").isEmpty())
                .andExpect(jsonPath("$.message").value(LlmConnectionTestResponse.MODELS_EMPTY_MESSAGE));
    }

    @Test
    void 연결_테스트_실패는_ok_false와_원인_메시지를_반환한다() throws Exception {
        fakeLlmClient.willListModels(() -> {
            throw new LlmException("로컬 LLM 서버(http://192.168.0.10:11434)에 연결할 수 없어요.");
        });

        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 11434}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.models").doesNotExist())
                .andExpect(jsonPath("$.message").value("로컬 LLM 서버(http://192.168.0.10:11434)에 연결할 수 없어요."));
    }

    @Test
    void 연결_테스트도_로컬_IP만_허용한다() throws Exception {
        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "169.254.169.254", "port": 11434}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("host"));
    }

    // ===================== 내부망 탐색 방지 (llm.guard) =====================

    @Test
    void 허용하지_않은_포트는_허용_포트를_안내하며_400으로_거부한다() throws Exception {
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 8080, "model": "qwen2.5:7b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("port"))
                .andExpect(jsonPath("$.errors[0].message").value(LlmHostGuard.PORT_MESSAGE_PREFIX + "1234, 11434"));

        mockMvc.perform(post("/api/settings/llm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.10", "port": 22}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("port"));
    }

    @Test
    void 연결_테스트를_너무_자주_하면_연결하지_않고_안내한다() throws Exception {
        for (int i = 1; i <= 10; i++) {
            testConnection("192.168.0." + i).andExpect(jsonPath("$.ok").value(true));
        }

        testConnection("192.168.0.11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(startsWith("LLM 서버 연결 시도가 너무 많아요.")));
    }

    @Test
    void 서버_주소를_너무_자주_바꾸면_429로_거부하고_같은_주소_저장은_허용한다() throws Exception {
        for (int i = 1; i <= 10; i++) {
            saveSetting("192.168.0." + i).andExpect(status().isOk());
        }

        saveSetting("192.168.0.11")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(startsWith("LLM 서버 연결 시도가 너무 많아요.")));
        saveSetting("192.168.0.10").andExpect(status().isOk());
    }

    private ResultActions testConnection(String host) throws Exception {
        return mockMvc.perform(post("/api/settings/llm/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"provider": "OLLAMA", "host": "%s", "port": 11434}
                        """.formatted(host)));
    }

    private ResultActions saveSetting(String host) throws Exception {
        return mockMvc.perform(put("/api/settings/llm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"provider": "OLLAMA", "host": "%s", "port": 11434, "model": "qwen2.5:7b"}
                        """.formatted(host)));
    }
}
