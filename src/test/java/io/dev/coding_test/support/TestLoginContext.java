package io.dev.coding_test.support;

import io.dev.coding_test.model.enums.UserRole;
import io.dev.coding_test.security.core.CustomUserPrincipal;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 테스트 MockMvc 요청에 붙일 로그인 회원.
 * <p>
 * {@link TestMockMvcCustomizer}가 모든 요청을 이 회원으로 로그인한 상태로 보낸다.
 * 요청마다 현재 값을 읽으므로 테스트에서 {@link #loginAs}(보통 {@link TestUsers#login})로 바꿀 수 있고,
 * 바꿨다면 {@link #reset()}으로 되돌린다. 기본 회원({@link #DEFAULT_USER})은 DB에 없으므로
 * 메모·LLM 설정을 다루는 테스트는 {@link TestUsers#login}으로 실제 회원을 만들어 쓴다.
 * </p>
 */
@Component
public class TestLoginContext {

    public static final CustomUserPrincipal DEFAULT_USER = new CustomUserPrincipal(1L, "tester", "테스터", UserRole.USER, null);

    private volatile CustomUserPrincipal current = DEFAULT_USER;

    public void loginAs(CustomUserPrincipal user) {
        this.current = user;
    }

    public CustomUserPrincipal current() {
        return current;
    }

    public void reset() {
        this.current = DEFAULT_USER;
    }

    MockHttpServletRequest apply(MockHttpServletRequest request) {
        CustomUserPrincipal user = current;
        if (user == null) {
            return request;
        }
        return authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()))
                .postProcessRequest(request);
    }
}
