package io.dev.coding_test.controller;

import io.dev.coding_test.common.handler.SecurityAuthenticationEntryPoint;
import io.dev.coding_test.common.security.CustomUserPrincipal;
import io.dev.coding_test.model.User;
import io.dev.coding_test.repository.UserRepository;
import io.dev.coding_test.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원가입 · 로그인 · 로그아웃과 로그인하지 않은 요청 처리를 검증한다.
 * 기본 테스트 설정(자동 로그인)을 쓰지 않도록 MockMvc를 직접 만든다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 회원가입하면_비밀번호는_BCrypt_해시로_저장하고_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("toast", "가입이 완료됐어요. 로그인해주세요."))
                .andExpect(flash().attribute("username", "hasegos"));

        User user = userRepository.findByUsername("hasegos").orElseThrow();
        assertThat(user.getNickname()).isEqualTo("하세고스");
        assertThat(user.getPassword()).startsWith("{bcrypt}$2").doesNotContain("secret123");
    }

    @Test
    void 비밀번호_확인이_다르면_에러와_함께_폼을_다시_보여주고_비밀번호는_비운다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret124", "하세고스"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"))
                .andExpect(content().string(containsString(UserService.PASSWORD_MISMATCH_MESSAGE)))
                .andExpect(content().string(not(containsString("secret123"))));

        assertThat(userRepository.existsByUsername("hasegos")).isFalse();
    }

    @Test
    void 이미_사용_중인_아이디는_가입할_수_없다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "첫번째"));

        mockMvc.perform(signup("hasegos", "other1234", "other1234", "두번째"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "username"))
                .andExpect(content().string(containsString(UserService.DUPLICATE_USERNAME_MESSAGE)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hasegos    | secret123 | 하세고스 | username",
            "abc        | secret123 | 하세고스 | username",
            "has egos   | secret123 | 하세고스 | username",
            "hasegos    | short1    | 하세고스 | password",
            "hasegos    | onlyletters | 하세고스 | password",
            "hasegos    | 12345678  | 하세고스 | password",
            "hasegos    | 비밀번호1234 | 하세고스 | password",
            "hasegos    | secret123 | 하      | nickname",
            "hasegos    | secret123 | <b>닉</b> | nickname",
    })
    void 입력_형식을_검증한다(String username, String password, String nickname, String field) throws Exception {
        mockMvc.perform(signup(username, password, password, nickname))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", field));
    }

    @Test
    void 로그인하면_세션에_회원_정보를_저장하고_비밀번호_해시는_남기지_않는다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));

        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "hasegos").param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos"))
                .andReturn();

        CustomUserPrincipal principal = principal((MockHttpSession) result.getRequest().getSession());
        assertThat(principal.getUsername()).isEqualTo("hasegos");
        assertThat(principal.getNickname()).isEqualTo("하세고스");
        assertThat(principal.getUserId()).isNotNull();
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void 로그인한_회원의_닉네임을_헤더_메뉴에_보여준다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                .param("username", "hasegos").param("password", "secret123")).andReturn().getRequest().getSession();

        mockMvc.perform(get("/memos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user-menu__name\">하세고스</span>")))
                .andExpect(content().string(containsString("data-theme-toggle")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    void 아이디나_비밀번호가_틀리면_같은_안내를_보여준다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));

        mockMvc.perform(post("/login").with(csrf()).param("username", "hasegos").param("password", "wrong1234"))
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(post("/login").with(csrf()).param("username", "nobody").param("password", "secret123"))
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("아이디 또는 비밀번호가 올바르지 않아요.")));
    }

    @Test
    void 로그인_요청에도_CSRF_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/login").param("username", "hasegos").param("password", "secret123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그아웃은_POST로만_받고_세션을_끝낸다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                .param("username", "hasegos").param("password", "secret123")).andReturn().getRequest().getSession();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 로그인하지_않은_화면_요청은_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/memos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/settings/llm"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 로그인하면_처음_요청했던_화면으로_돌아간다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/memos/new"))
                .andExpect(redirectedUrl("/login"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "hasegos").param("password", "secret123"))
                .andExpect(redirectedUrl("http://localhost/memos/new?continue"));
    }

    @Test
    void 로그인하지_않은_API_요청은_401_JSON을_반환한다() throws Exception {
        mockMvc.perform(get("/api/memos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(SecurityAuthenticationEntryPoint.MESSAGE));
    }

    @Test
    void 로그인_회원가입_화면과_정적_리소스는_로그인_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("user-menu"))));
        mockMvc.perform(get("/signup")).andExpect(status().isOk());
        mockMvc.perform(get("/css/pages/auth.css")).andExpect(status().isOk());
    }

    @Test
    void 이미_로그인했으면_로그인_회원가입_화면_대신_메모_목록으로_보낸다() throws Exception {
        mockMvc.perform(signup("hasegos", "secret123", "secret123", "하세고스"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                .param("username", "hasegos").param("password", "secret123")).andReturn().getRequest().getSession();

        mockMvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/memos"));
        mockMvc.perform(get("/signup").session(session)).andExpect(redirectedUrl("/memos"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder signup(String username, String password,
                                                                           String passwordConfirm, String nickname) {
        return post("/signup").with(csrf())
                .param("username", username)
                .param("password", password)
                .param("passwordConfirm", passwordConfirm)
                .param("nickname", nickname);
    }

    private static CustomUserPrincipal principal(MockHttpSession session) {
        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        return (CustomUserPrincipal) authentication.getPrincipal();
    }
}
