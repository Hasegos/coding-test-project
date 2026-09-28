package io.dev.coding_test.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 랜딩 요청을 처리하는 컨트롤러.
 */
@Controller
public class HomeController {

    /**
     * 루트 요청은 메모 목록으로 이동한다.
     *
     * @return 메모 목록 리다이렉트
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/memos";
    }
}
