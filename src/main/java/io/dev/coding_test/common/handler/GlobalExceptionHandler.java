package io.dev.coding_test.common.handler;

import io.dev.coding_test.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 화면(Thymeleaf) 요청 예외를 처리하는 핸들러.
 * <p>
 * 예외 종류에 따라 로그 레벨을 구분하여 기록하고,
 * 사용자에게는 단일 에러 페이지({@code error/error.html})를 실제 상태 코드와 함께 렌더링한다.
 * REST API 예외는 {@link ApiExceptionHandler}가 먼저 처리한다.
 * </p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 존재하지 않는 정적 리소스/경로 요청을 처리한다. (404)
     * <p>
     * favicon.ico, chrome devtools 등 브라우저 자동 요청으로 인한 ERROR 로그 오염을 막는다.
     * </p>
     *
     * @param e       발생한 NoResourceFoundException
     * @param request 현재 HTTP 요청
     * @param model   에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request, Model model) {
        log.warn("[404] URI: {}", request.getRequestURI());
        model.addAttribute("status", 404);
        model.addAttribute("message", "찾을 수 없는 페이지예요.");
        return "error/error";
    }

    /**
     * 리소스를 찾을 수 없을 때 발생하는 예외를 처리한다. (404)
     *
     * @param e     발생한 NotFoundException
     * @param model 에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFoundException(NotFoundException e, Model model) {
        log.warn("[404] NotFoundException 발생: {}", e.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("message", "찾을 수 없는 메모예요.");
        return "error/error";
    }

    /**
     * 클라이언트가 잘못 보낸 요청을 처리한다. (400)
     *
     * @param e       발생한 예외
     * @param request 현재 HTTP 요청
     * @param model   에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(Exception e, HttpServletRequest request, Model model) {
        log.warn("[400] 잘못된 요청 - URI: {}, {}", request.getRequestURI(), e.getClass().getSimpleName());
        model.addAttribute("status", 400);
        model.addAttribute("message", "잘못된 요청이에요.");
        return "error/error";
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 처리한다. (405)
     *
     * @param e       발생한 HttpRequestMethodNotSupportedException
     * @param request 현재 HTTP 요청
     * @param model   에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                           HttpServletRequest request, Model model) {
        log.warn("[405] 지원하지 않는 메서드 - {} {}", request.getMethod(), request.getRequestURI());
        model.addAttribute("status", 405);
        model.addAttribute("message", "잘못된 요청이에요.");
        return "error/error";
    }

    /**
     * 처리되지 않은 모든 예외를 처리한다. (500)
     *
     * @param e     발생한 Exception
     * @param model 에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e, Model model) {
        log.error("[500] 예상치 못한 예외 발생: {}", e.getMessage(), e);
        model.addAttribute("status", 500);
        model.addAttribute("message", "서버 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
        return "error/error";
    }
}
