package io.dev.coding_test.common.handler;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * REST API({@code @RestController}) 예외를 JSON으로 응답하는 핸들러.
 * <p>
 * 화면 요청 예외는 {@link GlobalExceptionHandler}가 에러 페이지로 처리하고,
 * API 요청 예외는 이 핸들러가 {@link ErrorResponse} 형식으로 응답한다.
 * </p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiExceptionHandler {

    /**
     * 리소스를 찾을 수 없을 때 발생하는 예외를 처리한다. (404)
     *
     * @param e 발생한 NotFoundException
     * @return 404 에러 응답
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        log.warn("[404] API NotFoundException 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, e.getMessage()));
    }

    /**
     * 요청 본문 검증(@Valid) 실패를 처리한다. (400)
     *
     * @param e 발생한 MethodArgumentNotValidException
     * @return 필드별 검증 실패 목록을 담은 400 에러 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("[400] API 검증 실패: {}", errors);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "입력값을 확인해주세요.", errors));
    }

    /**
     * 클라이언트가 잘못 보낸 요청을 처리한다. (400)
     * <p>
     * 파라미터 타입 불일치, 필수 파라미터 누락, 잘못된 JSON 본문.
     * </p>
     *
     * @param e       발생한 예외
     * @param request 현재 HTTP 요청
     * @return 400 에러 응답
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("[400] API 잘못된 요청 - URI: {}, {}", request.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "잘못된 요청이에요."));
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 처리한다. (405)
     *
     * @param e       발생한 HttpRequestMethodNotSupportedException
     * @param request 현재 HTTP 요청
     * @return 405 에러 응답
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                  HttpServletRequest request) {
        log.warn("[405] API 지원하지 않는 메서드 - {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(405, "지원하지 않는 요청 방식이에요."));
    }

    /**
     * 처리되지 않은 모든 예외를 처리한다. (500)
     *
     * @param e 발생한 Exception
     * @return 500 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[500] API 예상치 못한 예외 발생: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "서버 오류가 발생했어요. 잠시 후 다시 시도해주세요."));
    }
}
