package io.dev.coding_test.support;

import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.model.User;
import io.dev.coding_test.repository.UserRepository;
import io.dev.coding_test.security.core.CustomUserPrincipal;
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
public class TestUsers {

    private final UserRepository userRepository;
    private final TestLoginContext testLoginContext;

    /**
     * 회원을 저장한다.
     *
     * @param username 아이디 (닉네임은 아이디 앞에 "닉"을 붙인다)
     * @return 저장한 회원의 로그인 정보
     */
    public CustomUserPrincipal create(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("{noop}test-password");
        user.setNickname("닉" + username);
        user.setCreatedAt(TimeUtil.now());
        userRepository.save(user);
        return new CustomUserPrincipal(user.getUserId(), username, user.getNickname(), user.getRole(), null);
    }

    /**
     * 회원을 저장하고 이후 MockMvc 요청을 그 회원으로 로그인한 상태로 보낸다. 테스트 후 {@link TestLoginContext#reset()} 필요.
     *
     * @param username 아이디
     * @return 저장한 회원의 로그인 정보
     */
    public CustomUserPrincipal login(String username) {
        CustomUserPrincipal user = create(username);
        testLoginContext.loginAs(user);
        return user;
    }
}
