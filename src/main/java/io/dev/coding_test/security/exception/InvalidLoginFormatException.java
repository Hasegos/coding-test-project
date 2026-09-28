package io.dev.coding_test.security.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * 로그인 입력값이 아이디(이메일) · 비밀번호 형식에 맞지 않을 때 발생하는 인증 예외.
 * <p>
 * 형식이 틀린 요청은 DB를 조회하지 않고 거부하며, 메시지는 로그인 화면에 그대로 보여준다.
 * </p>
 */
public class InvalidLoginFormatException extends AuthenticationException {

    public InvalidLoginFormatException(String message) {
        super(message);
    }
}
