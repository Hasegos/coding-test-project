package io.dev.coding_test.controller;

import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingPageControllerTest {

    @Autowired
    private TestMembers testMembers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long memberId;

    @BeforeEach
    void loginMember() {
        memberId = testMembers.login("tester").getMemberId();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LlmSettingService llmSettingService;

    @Test
    void 설정_전에는_기본_포트와_미설정_상태를_보여준다() throws Exception {
        mockMvc.perform(get("/settings/llm"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/llm"))
                .andExpect(model().attribute("llmConfigured", false))
                .andExpect(content().string(containsString("value=\"11434\"")))
                .andExpect(content().string(containsString("미설정")));
    }

    @Test
    void 저장된_설정을_폼에_채우고_API_Key_값은_노출하지_않는다() throws Exception {
        llmSettingService.save(memberId, new LlmSettingRequest(LlmProvider.LMSTUDIO, "100.66.180.73", 1234,
                "qwen2.5-vl-7b-instruct", "secret-token", false));

        mockMvc.perform(get("/settings/llm"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("llmConfigured", true))
                .andExpect(content().string(containsString("value=\"100.66.180.73\"")))
                .andExpect(content().string(containsString("저장된 키 사용 중")))
                .andExpect(content().string(not(containsString("secret-token"))));
    }

    @Test
    void 저장_성공시_설정_화면으로_리다이렉트한다() throws Exception {
        mockMvc.perform(post("/settings/llm")
                        .param("provider", "LMSTUDIO")
                        .param("host", "100.66.180.73")
                        .param("port", "1234")
                        .param("model", "qwen2.5-vl-7b-instruct"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/llm"))
                .andExpect(flash().attribute("toast", "LLM 설정을 저장했어요."));
    }

    @Test
    void 로컬_IP가_아니면_에러와_함께_폼을_다시_보여준다() throws Exception {
        mockMvc.perform(post("/settings/llm")
                        .param("provider", "OLLAMA")
                        .param("host", "8.8.8.8")
                        .param("port", "11434")
                        .param("model", "qwen2.5:7b")
                        .param("apiKey", "typed-secret"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/llm"))
                .andExpect(model().attributeHasFieldErrors("llmSettingRequest", "host"))
                .andExpect(content().string(containsString("공인 IP는 사용할 수 없어요")))
                .andExpect(content().string(not(containsString("typed-secret"))));
    }

    @Test
    void 설정_전에는_메모_목록에_안내_배너를_보여준다() throws Exception {
        mockMvc.perform(get("/memos"))
                .andExpect(content().string(containsString("LLM 서버가 아직 연결되지 않았어요")))
                .andExpect(content().string(containsString("header__dot")));

        llmSettingService.save(memberId, new LlmSettingRequest(LlmProvider.OLLAMA, "192.168.0.10", 11434, "qwen2.5:7b", null, false));

        mockMvc.perform(get("/memos"))
                .andExpect(content().string(not(containsString("LLM 서버가 아직 연결되지 않았어요"))))
                .andExpect(content().string(not(containsString("header__dot\""))));
    }
}
