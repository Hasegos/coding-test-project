package io.dev.coding_test.security.exception;

import org.springframework.security.core.AuthenticationException;

import java.time.Duration;

/**
 * 로그인 실패가 반복되어 잠시 로그인을 막았을 때 발생하는 인증 예외.
 * <p>
 * 잠긴 동안에는 비밀번호가 맞아도 로그인할 수 없으며, 메시지는 로그인 화면에 그대로 보여준다.
 * </p>
 */
public class LoginLockedException extends AuthenticationException {

    public LoginLockedException(Duration remaining) {
        super(message(remaining));
    }

    /**
     * 남은 잠금 시간을 분 단위(올림)로 안내하는 메시지를 만든다.
     *
     * @param remaining 남은 잠금 시간
     * @return 안내 메시지
     */
    public static String message(Duration remaining) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return "로그인 시도가 너무 많아요. " + minutes + "분 후 다시 시도해주세요.";
    }
}
