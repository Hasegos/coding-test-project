package io.dev.coding_test.controller;

import io.dev.coding_test.common.handler.SecurityAccessDeniedHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 접근 거부(CSRF 토큰 오류 등) 에러 페이지를 렌더링하는 컨트롤러.
 * {@link SecurityAccessDeniedHandler}가 화면 요청을 이 경로로 전달한다.
 */
@Controller
public class AccessDeniedController {

    /**
     * 403 에러 페이지를 렌더링한다. 원래 요청 메서드(POST 등)로 전달되므로 모든 메서드를 받는다.
     *
     * @param model 에러 메시지를 뷰에 전달하기 위한 모델
     * @return 에러 페이지 뷰 이름
     */
    @RequestMapping(SecurityAccessDeniedHandler.ERROR_PAGE_PATH)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("message", SecurityAccessDeniedHandler.MESSAGE);
        return "error/error";
    }
}
