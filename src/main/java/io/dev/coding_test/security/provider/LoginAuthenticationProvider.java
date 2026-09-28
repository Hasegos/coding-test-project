package io.dev.coding_test.security.provider;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.security.attempt.LoginAttemptService;
import io.dev.coding_test.security.exception.InvalidLoginFormatException;
import io.dev.coding_test.security.exception.LoginLockedException;
import io.dev.coding_test.security.userdetails.CustomUserDetailService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 폼 로그인 인증 Provider.
 * <p>
 * {@link DaoAuthenticationProvider}(회원 조회 + BCrypt 비밀번호 비교)에 앞서 입력 형식을 회원가입과 같은 규칙으로 검사한다.
 * </p>
 * <ol>
 *     <li>로그인 실패가 반복되어 잠긴 IP·아이디면 비밀번호를 확인하지 않고 {@link LoginLockedException}으로 거부한다.</li>
 *     <li>아이디가 이메일 형식이 아니거나 비밀번호가 영문·숫자·특수문자 8~64자가 아니면
 *         DB를 조회하지 않고 {@link InvalidLoginFormatException}으로 거부한다.</li>
 *     <li>아이디는 가입할 때와 같이 앞뒤 공백을 지우고 소문자로 바꿔 조회한다.</li>
 *     <li>회원이 없거나 비밀번호가 틀리면 구분하지 않고 같은 예외(BadCredentials)를 던진다. (아이디 존재 여부 노출 방지)</li>
 *     <li>비밀번호가 틀린 횟수를 {@link LoginAttemptService}에 기록하고, 이번 실패로 잠기면 바로 잠금 안내를 보여준다.</li>
 * </ol>
 */
@Component
public class LoginAuthenticationProvider extends DaoAuthenticationProvider {

    private final LoginAttemptService loginAttemptService;

    public LoginAuthenticationProvider(CustomUserDetailService userDetailService, PasswordEncoder passwordEncoder,
                                       LoginAttemptService loginAttemptService) {
        super(userDetailService);
        setPasswordEncoder(passwordEncoder);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? null : credentials.toString();
        String ip = remoteAddress(authentication.getDetails());

        Optional<Duration> locked = loginAttemptService.lockedFor(ip, username);
        if (locked.isPresent()) {
            throw new LoginLockedException(locked.get());
        }
        if (!AuthPattern.isValidUsername(username)) {
            throw new InvalidLoginFormatException(AuthPattern.USERNAME_MESSAGE);
        }
        if (!AuthPattern.isValidPassword(password)) {
            throw new InvalidLoginFormatException(AuthPattern.PASSWORD_MESSAGE);
        }

        UsernamePasswordAuthenticationToken normalized = UsernamePasswordAuthenticationToken.unauthenticated(
                AuthPattern.normalizeUsername(username), password);
        normalized.setDetails(authentication.getDetails());
        Authentication result;
        try {
            result = super.authenticate(normalized);
        } catch (BadCredentialsException e) {
            Optional<Duration> lockedNow = loginAttemptService.recordFailure(ip, username);
            if (lockedNow.isPresent()) {
                throw new LoginLockedException(lockedNow.get());
            }
            throw e;
        }
        loginAttemptService.recordSuccess(ip, username);
        return result;
    }

    /**
     * 요청 IP. 프록시 뒤에서 실행하면 {@code server.forward-headers-strategy}를 설정해야 실제 사용자 IP가 된다.
     */
    private static String remoteAddress(Object details) {
        if (details instanceof WebAuthenticationDetails web && web.getRemoteAddress() != null) {
            return web.getRemoteAddress();
        }
        return "unknown";
    }
}
