package io.dev.coding_test.controller;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.service.MemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 메모 화면(Thymeleaf) 요청을 처리하는 컨트롤러.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/memos")
public class MemoPageController {

    private final MemoService memoService;

    /**
     * 메모 작성 페이지를 렌더링한다.
     *
     * @param model 뷰에 전달할 데이터 모델
     * @return 메모 작성 폼 뷰 이름
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("memoRequest", new MemoRequest());
        return "memo/form";
    }

    /**
     * 메모를 저장하고 상세 페이지로 이동한다(PRG).
     * 검증 실패 시 입력값을 유지한 채 작성 폼을 다시 렌더링한다.
     *
     * @param request            메모 작성 요청
     * @param bindingResult      검증 결과
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 상세 페이지 리다이렉트 또는 작성 폼 뷰 이름
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("memoRequest") MemoRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "memo/form";
        }
        MemoResponse memo = memoService.create(request);
        redirectAttributes.addFlashAttribute("toast", "메모를 저장했어요.");
        return "redirect:/memos/" + memo.memoId();
    }
}
