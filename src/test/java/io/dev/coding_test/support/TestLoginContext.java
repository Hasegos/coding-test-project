package io.dev.coding_test.support;

import io.dev.coding_test.common.security.LoginMember;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 테스트 MockMvc 요청에 붙일 로그인 회원.
 * <p>
 * {@link TestMockMvcCustomizer}가 모든 요청을 이 회원으로 로그인한 상태로 보낸다.
 * 요청마다 현재 값을 읽으므로 테스트에서 {@link #loginAs}(보통 {@link TestMembers#login})로 바꿀 수 있고,
 * 바꿨다면 {@link #reset()}으로 되돌린다. 기본 회원({@link #DEFAULT_MEMBER})은 DB에 없으므로
 * 메모·LLM 설정을 다루는 테스트는 {@link TestMembers#login}으로 실제 회원을 만들어 쓴다.
 * </p>
 */
@Component
public class TestLoginContext {

    public static final LoginMember DEFAULT_MEMBER = new LoginMember(1L, "tester", "테스터", null);

    private volatile LoginMember current = DEFAULT_MEMBER;

    public void loginAs(LoginMember member) {
        this.current = member;
    }

    public LoginMember current() {
        return current;
    }

    public void reset() {
        this.current = DEFAULT_MEMBER;
    }

    MockHttpServletRequest apply(MockHttpServletRequest request) {
        LoginMember member = current;
        if (member == null) {
            return request;
        }
        return authentication(UsernamePasswordAuthenticationToken.authenticated(member, null, member.getAuthorities()))
                .postProcessRequest(request);
    }
}
