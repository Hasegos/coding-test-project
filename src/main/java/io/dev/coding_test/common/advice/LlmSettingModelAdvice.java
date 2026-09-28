package io.dev.coding_test.common.advice;

import io.dev.coding_test.common.security.LoginMemberId;
import io.dev.coding_test.controller.MemoPageController;
import io.dev.coding_test.controller.SettingPageController;
import io.dev.coding_test.service.LlmSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 화면(Thymeleaf) 요청에 로그인한 회원의 LLM 서버 설정 여부를 공통으로 전달한다.
 * <p>
 * 헤더의 'LLM 설정' 알림 표시와 미설정 안내 배너에 사용한다.
 * 요약 상태 폴링 같은 REST API 요청에서는 조회하지 않도록 화면 컨트롤러에만 적용한다.
 * </p>
 */
@RequiredArgsConstructor
@ControllerAdvice(assignableTypes = {MemoPageController.class, SettingPageController.class})
public class LlmSettingModelAdvice {

    private final LlmSettingService llmSettingService;

    @ModelAttribute("llmConfigured")
    public boolean llmConfigured(@LoginMemberId Long memberId) {
        return llmSettingService.isConfigured(memberId);
    }
}
