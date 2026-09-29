package io.dev.coding_test.security.handler;

import io.dev.coding_test.dto.common.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CSRF 토큰이 없거나 올바르지 않은 요청을 처리하는 핸들러. (403)
 * <p>
 * API 요청은 {@link ErrorResponse} JSON으로, 화면 요청은 공통 에러 페이지로 응답한다.
 * </p>
 */
@Slf4j
@Component
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MESSAGE = "요청이 만료되었거나 올바르지 않아요. 페이지를 새로고침한 뒤 다시 시도해주세요.";
    public static final String ERROR_PAGE_PATH = "/access-denied";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException, ServletException {
        log.warn("[403] 접근 거부 - {} {}, {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        if (request.getRequestURI().startsWith("/api/")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JsonMapper.shared().writeValueAsString(ErrorResponse.of(403, MESSAGE)));
            return;
        }
        request.getRequestDispatcher(ERROR_PAGE_PATH).forward(request, response);
    }
}
