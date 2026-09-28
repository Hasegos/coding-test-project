package io.dev.coding_test.security.config;

import io.dev.coding_test.security.core.CustomUserPrincipal;
import io.dev.coding_test.security.handler.SecurityAccessDeniedHandler;
import io.dev.coding_test.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CSRF 차단과 보안 헤더를 검증한다.
 * 기본 테스트 설정(모든 요청에 CSRF 토큰 추가)을 쓰지 않도록 MockMvc를 직접 만들고, 로그인한 상태로 요청한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestUsers testUsers;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserPrincipal user = testUsers.create("tester");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()))))
                .build();
    }

    @Test
    void CSRF_토큰_없는_화면_폼_요청은_403_에러_페이지를_보여준다() throws Exception {
        mockMvc.perform(post("/memos").param("title", "제목").param("content", "본문"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl(SecurityAccessDeniedHandler.ERROR_PAGE_PATH));

        mockMvc.perform(post("/memos/1/delete"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 접근_거부_에러_페이지는_403과_안내_메시지를_보여준다() throws Exception {
        mockMvc.perform(get(SecurityAccessDeniedHandler.ERROR_PAGE_PATH))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString(SecurityAccessDeniedHandler.MESSAGE)));
    }

    @Test
    void CSRF_토큰_없는_API_변경_요청은_403_JSON을_반환한다() throws Exception {
        mockMvc.perform(post("/api/memos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"제목\", \"content\": \"본문\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(SecurityAccessDeniedHandler.MESSAGE));

        mockMvc.perform(delete("/api/memos/1")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/settings/llm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/memos/1/summary")).andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_CSRF_토큰은_거부한다() throws Exception {
        mockMvc.perform(post("/memos").param("title", "제목").param("content", "본문").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 유효한_CSRF_토큰이_있으면_처리한다() throws Exception {
        mockMvc.perform(post("/memos").param("title", "제목").param("content", "본문").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void 조회_요청은_CSRF_토큰_없이_허용한다() throws Exception {
        mockMvc.perform(get("/memos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/memos")).andExpect(status().isOk());
    }

    @Test
    void 화면에_CSRF_토큰을_심고_폼에_자동으로_넣는다() throws Exception {
        mockMvc.perform(get("/memos/new"))
                .andExpect(content().string(containsString("<meta name=\"_csrf\"")))
                .andExpect(content().string(containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void 보안_헤더를_응답한다() throws Exception {
        mockMvc.perform(get("/memos"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void 화면에_인라인_스크립트가_없다() throws Exception {
        mockMvc.perform(get("/memos"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>"))));
    }
}
