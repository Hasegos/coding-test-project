package io.dev.coding_test.security.handler;

import io.dev.coding_test.security.config.SecurityConfig;
import io.dev.coding_test.security.exception.InvalidLoginFormatException;
import io.dev.coding_test.security.exception.LoginLockedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 실패 시 원인에 맞는 안내 메시지를 세션에 담고 로그인 화면으로 보낸다.
 * <p>
 * 로그인 화면({@code AuthController})은 메시지와 입력했던 아이디를 한 번만 꺼내 보여주고 세션에서 지운다.
 * URL 파라미터로 메시지를 넘기지 않아 임의 문구를 화면에 띄우는 것을 막는다.
 * </p>
 */
@Slf4j
@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    public static final String LOGIN_ERROR = "loginError";
    public static final String LOGIN_USERNAME = "loginUsername";

    public static final String BAD_CREDENTIALS_MESSAGE = "아이디(이메일) 또는 비밀번호가 올바르지 않아요.";
    public static final String UNKNOWN_MESSAGE = "로그인 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String message;
        if (exception instanceof InvalidLoginFormatException || exception instanceof LoginLockedException) {
            message = exception.getMessage();
        } else if (exception instanceof BadCredentialsException) {
            message = BAD_CREDENTIALS_MESSAGE;
        } else {
            log.warn("로그인 실패 - {}", exception.getClass().getSimpleName());
            message = UNKNOWN_MESSAGE;
        }

        HttpSession session = request.getSession();
        session.setAttribute(LOGIN_ERROR, message);
        String username = request.getParameter("username");
        if (username != null && username.length() <= 100) {
            session.setAttribute(LOGIN_USERNAME, username.strip());
        }
        response.sendRedirect(request.getContextPath() + SecurityConfig.LOGIN_PATH);
    }
}
