package io.dev.coding_test.security.provider;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.security.exception.InvalidLoginFormatException;
import io.dev.coding_test.security.userdetails.CustomUserDetailService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 폼 로그인 인증 Provider.
 * <p>
 * {@link DaoAuthenticationProvider}(회원 조회 + BCrypt 비밀번호 비교)에 앞서 입력 형식을 회원가입과 같은 규칙으로 검사한다.
 * </p>
 * <ol>
 *     <li>아이디가 이메일 형식이 아니거나 비밀번호가 영문·숫자·특수문자 8~64자가 아니면
 *         DB를 조회하지 않고 {@link InvalidLoginFormatException}으로 거부한다.</li>
 *     <li>아이디는 가입할 때와 같이 앞뒤 공백을 지우고 소문자로 바꿔 조회한다.</li>
 *     <li>회원이 없거나 비밀번호가 틀리면 구분하지 않고 같은 예외(BadCredentials)를 던진다. (아이디 존재 여부 노출 방지)</li>
 * </ol>
 */
@Component
public class LoginAuthenticationProvider extends DaoAuthenticationProvider {

    public LoginAuthenticationProvider(CustomUserDetailService userDetailService, PasswordEncoder passwordEncoder) {
        super(userDetailService);
        setPasswordEncoder(passwordEncoder);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? null : credentials.toString();

        if (!AuthPattern.isValidUsername(username)) {
            throw new InvalidLoginFormatException(AuthPattern.USERNAME_MESSAGE);
        }
        if (!AuthPattern.isValidPassword(password)) {
            throw new InvalidLoginFormatException(AuthPattern.PASSWORD_MESSAGE);
        }

        UsernamePasswordAuthenticationToken normalized = UsernamePasswordAuthenticationToken.unauthenticated(
                AuthPattern.normalizeUsername(username), password);
        normalized.setDetails(authentication.getDetails());
        return super.authenticate(normalized);
    }
}
