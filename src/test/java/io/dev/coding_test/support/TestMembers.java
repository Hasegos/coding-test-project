package io.dev.coding_test.support;

import io.dev.coding_test.common.security.LoginMember;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.model.Member;
import io.dev.coding_test.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 테스트용 회원 생성 도우미.
 * <p>
 * 메모·LLM 설정은 회원(외래키)에 속하므로 테스트마다 실제 회원 행을 만들고,
 * {@link #login}은 MockMvc 요청도 그 회원으로 로그인한 상태가 되게 한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TestMembers {

    private final MemberRepository memberRepository;
    private final TestLoginContext testLoginContext;

    /**
     * 회원을 저장한다.
     *
     * @param username 아이디 (닉네임은 아이디 앞에 "닉"을 붙인다)
     * @return 저장한 회원의 로그인 정보
     */
    public LoginMember create(String username) {
        Member member = new Member();
        member.setUsername(username);
        member.setPassword("{noop}test-password");
        member.setNickname("닉" + username);
        member.setCreatedAt(TimeUtil.now());
        memberRepository.save(member);
        return new LoginMember(member.getMemberId(), username, member.getNickname(), null);
    }

    /**
     * 회원을 저장하고 이후 MockMvc 요청을 그 회원으로 로그인한 상태로 보낸다. 테스트 후 {@link TestLoginContext#reset()} 필요.
     *
     * @param username 아이디
     * @return 저장한 회원의 로그인 정보
     */
    public LoginMember login(String username) {
        LoginMember member = create(username);
        testLoginContext.loginAs(member);
        return member;
    }
}
