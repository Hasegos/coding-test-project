package io.dev.coding_test.controller;

import io.dev.coding_test.common.security.LoginMemberId;
import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.dto.LlmSettingResponse;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.service.MemoSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * LLM 설정 화면(Thymeleaf) 요청을 처리하는 컨트롤러.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/settings/llm")
public class SettingPageController {

    private final LlmSettingService llmSettingService;
    private final MemoSummaryService memoSummaryService;

    /**
     * LLM 설정 화면을 렌더링한다. 저장된 설정이 있으면 폼에 채워서 보여준다. (API Key 값은 채우지 않음)
     *
     * @param memberId 로그인한 회원 ID
     * @param model    뷰에 전달할 데이터 모델
     * @return LLM 설정 뷰 이름
     */
    @GetMapping
    public String form(@LoginMemberId Long memberId,
                       Model model) {
        LlmSettingRequest request = llmSettingService.getSetting(memberId)
                .map(saved -> new LlmSettingRequest(saved.provider(), saved.host(), saved.port(),
                        saved.model(), null, false))
                .orElseGet(() -> new LlmSettingRequest(LlmProvider.OLLAMA, null,
                        LlmProvider.OLLAMA.getDefaultPort(), null, null, false));
        model.addAttribute("llmSettingRequest", request);
        addFormAttributes(memberId, model);
        return "settings/llm";
    }

    /**
     * 접속 설정을 저장하고, 요약에 실패했던 메모를 다시 요약 요청한다(PRG).
     * 검증 실패 시 입력값을 유지한 채 설정 화면을 다시 렌더링한다.
     *
     * @param memberId           로그인한 회원 ID
     * @param request            접속 설정 저장 요청
     * @param bindingResult      검증 결과
     * @param model              뷰에 전달할 데이터 모델
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 설정 화면 리다이렉트 또는 뷰 이름
     */
    @PostMapping
    public String save(@LoginMemberId Long memberId,
                       @Valid @ModelAttribute("llmSettingRequest") LlmSettingRequest request,
                       BindingResult bindingResult,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            request.setApiKey(null);
            addFormAttributes(memberId, model);
            return "settings/llm";
        }
        llmSettingService.save(memberId, request);
        int retried = memoSummaryService.retryFailed(memberId);
        redirectAttributes.addFlashAttribute("toast", retried > 0
                ? "LLM 설정을 저장했어요. 실패한 요약 " + retried + "건을 다시 요청했어요."
                : "LLM 설정을 저장했어요.");
        return "redirect:/settings/llm";
    }

    private void addFormAttributes(Long memberId, Model model) {
        model.addAttribute("providers", LlmProvider.values());
        model.addAttribute("hasApiKey", llmSettingService.getSetting(memberId).map(LlmSettingResponse::hasApiKey).orElse(false));
    }
}
