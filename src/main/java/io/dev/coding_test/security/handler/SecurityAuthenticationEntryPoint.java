package io.dev.coding_test.security.handler;

import io.dev.coding_test.dto.common.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 로그인하지 않은 요청을 처리한다.
 * <p>
 * API 요청은 로그인 화면으로 리다이렉트하지 않고 {@link ErrorResponse} JSON(401)으로 응답하고,
 * 화면 요청은 로그인 화면으로 보낸다. (로그인 후 원래 요청한 화면으로 돌아감)
 * </p>
 */
@Component
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String MESSAGE = "로그인이 필요해요. 다시 로그인해주세요.";

    private final LoginUrlAuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException, ServletException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JsonMapper.shared().writeValueAsString(ErrorResponse.of(401, MESSAGE)));
            return;
        }
        loginEntryPoint.commence(request, response, e);
    }
}
