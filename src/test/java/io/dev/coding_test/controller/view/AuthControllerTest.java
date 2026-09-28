package io.dev.coding_test.controller.view;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.model.User;
import io.dev.coding_test.model.enums.UserRole;
import io.dev.coding_test.repository.UserRepository;
import io.dev.coding_test.security.core.CustomUserPrincipal;
import io.dev.coding_test.security.handler.CustomAuthFailureHandler;
import io.dev.coding_test.security.handler.SecurityAuthenticationEntryPoint;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

    private static final String EMAIL = "hasegos@example.com";
    private static final String PASSWORD = "secret12!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ===================== 회원가입 =====================

    @Test
    void 회원가입하면_이메일은_소문자로_비밀번호는_BCrypt_해시로_저장하고_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(signup("  HaseGos@Example.com ", PASSWORD, PASSWORD, "하세고스"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("toast", "가입이 완료됐어요. 로그인해주세요."))
                .andExpect(flash().attribute(CustomAuthFailureHandler.LOGIN_USERNAME, EMAIL));

        User user = userRepository.findByUsername(EMAIL).orElseThrow();
        assertThat(user.getNickname()).isEqualTo("하세고스");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPassword()).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void 비밀번호_확인이_다르면_에러와_함께_폼을_다시_보여주고_비밀번호는_비운다() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, "secret12?", "하세고스"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"))
                .andExpect(content().string(containsString(UserService.PASSWORD_MISMATCH_MESSAGE)))
                .andExpect(content().string(not(containsString(PASSWORD))));

        assertThat(userRepository.existsByUsername(EMAIL)).isFalse();
    }

    @Test
    void 이미_가입된_이메일은_대소문자가_달라도_가입할_수_없다() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "첫번째"));

        mockMvc.perform(signup("HASEGOS@example.com", "other12!@", "other12!@", "두번째"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "username"))
                .andExpect(content().string(containsString(UserService.DUPLICATE_USERNAME_MESSAGE)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // 아이디: 이메일 형식
            "hasegos                | secret12!  | 하세고스 | username",
            "hasegos@               | secret12!  | 하세고스 | username",
            "@example.com           | secret12!  | 하세고스 | username",
            "hasegos@example        | secret12!  | 하세고스 | username",
            "hase gos@example.com   | secret12!  | 하세고스 | username",
            "hase..gos@example.com  | secret12!  | 하세고스 | username",
            "hasegos@example.c      | secret12!  | 하세고스 | username",
            // 비밀번호: 영문 + 숫자 + 특수문자, 8~64자
            "hasegos@example.com    | secret12   | 하세고스 | password",
            "hasegos@example.com    | secret!!   | 하세고스 | password",
            "hasegos@example.com    | 12345678!  | 하세고스 | password",
            "hasegos@example.com    | se12!      | 하세고스 | password",
            "hasegos@example.com    | 비밀번호12!  | 하세고스 | password",
            "hasegos@example.com    | secret 12! | 하세고스 | password",
            // 닉네임
            "hasegos@example.com    | secret12!  | 하      | nickname",
            "hasegos@example.com    | secret12!  | <b>닉</b> | nickname",
    })
    void 회원가입_입력_형식을_검증한다(String username, String password, String nickname, String field) throws Exception {
        mockMvc.perform(signup(username, password, password, nickname))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", field));
    }

    @Test
    void 비밀번호_상한은_64자다() throws Exception {
        String max = "a1!" + "x".repeat(61);
        String over = max + "y";

        mockMvc.perform(signup("max@example.com", over, over, "상한초과"))
                .andExpect(model().attributeHasFieldErrors("signupRequest", "password"));
        mockMvc.perform(signup("max@example.com", max, max, "상한"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 이메일_상한은_100자다() throws Exception {
        String over = "a".repeat(89) + "@example.com";

        mockMvc.perform(signup(over, PASSWORD, PASSWORD, "상한초과"))
                .andExpect(model().attributeHasFieldErrors("signupRequest", "username"));
    }

    // ===================== 로그인 =====================

    @Test
    void 로그인하면_세션에_회원_정보를_저장하고_비밀번호_해시는_남기지_않는다() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "하세고스"));

        MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos"))
                .andReturn();

        CustomUserPrincipal principal = principal((MockHttpSession) result.getRequest().getSession());
        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getNickname()).isEqualTo("하세고스");
        assertThat(principal.getUserId()).isNotNull();
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void 로그인_이메일은_대소문자와_앞뒤_공백을_무시한다() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "하세고스"));

        mockMvc.perform(login("  HaseGos@EXAMPLE.com ", PASSWORD))
                .andExpect(redirectedUrl("/memos"));
    }

    @Test
    void 로그인한_회원의_닉네임을_헤더_메뉴에_보여준다() throws Exception {
        MockHttpSession session = signupAndLogin();

        mockMvc.perform(get("/memos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user-menu__name\">하세고스</span>")))
                .andExpect(content().string(containsString("data-theme-toggle")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "hasegos       | secret12!",
            "hasegos@      | secret12!",
            "' '           | secret12!",
    })
    void 로그인_아이디가_이메일_형식이_아니면_DB를_조회하지_않고_형식_안내를_보여준다(String username, String password)
            throws Exception {
        assertLoginError(username, password, AuthPattern.USERNAME_MESSAGE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "hasegos@example.com | secret12",
            "hasegos@example.com | short1!",
            "hasegos@example.com | ''",
    })
    void 로그인_비밀번호가_형식에_맞지_않으면_형식_안내를_보여준다(String username, String password) throws Exception {
        assertLoginError(username, password, AuthPattern.PASSWORD_MESSAGE);
    }

    @Test
    void 가입되지_않은_이메일과_틀린_비밀번호는_같은_안내를_보여준다() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "하세고스"));

        assertLoginError(EMAIL, "wrong12!@", CustomAuthFailureHandler.BAD_CREDENTIALS_MESSAGE);
        assertLoginError("nobody@example.com", PASSWORD, CustomAuthFailureHandler.BAD_CREDENTIALS_MESSAGE);
    }

    @Test
    void 로그인_실패_안내는_한_번만_보여주고_입력한_이메일은_남겨둔다() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(redirectedUrl("/login"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString(CustomAuthFailureHandler.BAD_CREDENTIALS_MESSAGE)))
                .andExpect(content().string(containsString("value=\"nobody@example.com\"")));
        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(not(containsString(CustomAuthFailureHandler.BAD_CREDENTIALS_MESSAGE))));
    }

    @Test
    void 로그인_요청에도_CSRF_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/login").param("username", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그아웃은_POST로만_받고_세션을_끝낸다() throws Exception {
        MockHttpSession session = signupAndLogin();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(session.isInvalid()).isTrue();
    }

    // ===================== 접근 제어 =====================

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
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "하세고스"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/memos/new"))
                .andExpect(redirectedUrl("/login"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(login(EMAIL, PASSWORD).session(session))
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
        mockMvc.perform(get("/js/auth.js")).andExpect(status().isOk());
    }

    @Test
    void 이미_로그인했으면_로그인_회원가입_화면_대신_메모_목록으로_보낸다() throws Exception {
        MockHttpSession session = signupAndLogin();

        mockMvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/memos"));
        mockMvc.perform(get("/signup").session(session)).andExpect(redirectedUrl("/memos"));
    }

    private void assertLoginError(String username, String password, String message) throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(login(username, password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn().getRequest().getSession();

        assertThat(session.getAttribute(CustomAuthFailureHandler.LOGIN_ERROR)).isEqualTo(message);
        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString(message)));
    }

    private MockHttpSession signupAndLogin() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, PASSWORD, "하세고스"));
        return (MockHttpSession) mockMvc.perform(login(EMAIL, PASSWORD)).andReturn().getRequest().getSession();
    }

    private static RequestBuilder signup(String username, String password, String passwordConfirm, String nickname) {
        return post("/signup").with(csrf())
                .param("username", username)
                .param("password", password)
                .param("passwordConfirm", passwordConfirm)
                .param("nickname", nickname);
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/login").with(csrf()).param("username", username).param("password", password);
    }

    private static CustomUserPrincipal principal(MockHttpSession session) {
        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        return (CustomUserPrincipal) authentication.getPrincipal();
    }
}
