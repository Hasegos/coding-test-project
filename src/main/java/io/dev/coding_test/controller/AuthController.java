package io.dev.coding_test.controller;

import io.dev.coding_test.common.config.SecurityConfig;
import io.dev.coding_test.common.exception.DuplicateUsernameException;
import io.dev.coding_test.common.security.LoginMember;
import io.dev.coding_test.dto.SignupRequest;
import io.dev.coding_test.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 로그인·회원가입 화면(Thymeleaf) 요청을 처리하는 컨트롤러.
 * <p>
 * 로그인 처리(POST /login)와 로그아웃(POST /logout)은 Spring Security가 담당한다.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    /**
     * 로그인 화면을 렌더링한다. 이미 로그인했으면 메모 목록으로 보낸다.
     *
     * @param loginMember 로그인한 회원, 없으면 {@code null}
     * @return 로그인 뷰 이름 또는 메모 목록 리다이렉트
     */
    @GetMapping(SecurityConfig.LOGIN_PATH)
    public String loginForm(@AuthenticationPrincipal LoginMember loginMember) {
        return loginMember != null ? "redirect:/memos" : "auth/login";
    }

    /**
     * 회원가입 화면을 렌더링한다. 이미 로그인했으면 메모 목록으로 보낸다.
     *
     * @param loginMember 로그인한 회원, 없으면 {@code null}
     * @param model       뷰에 전달할 데이터 모델
     * @return 회원가입 뷰 이름 또는 메모 목록 리다이렉트
     */
    @GetMapping(SecurityConfig.SIGNUP_PATH)
    public String signupForm(@AuthenticationPrincipal LoginMember loginMember, Model model) {
        if (loginMember != null) {
            return "redirect:/memos";
        }
        model.addAttribute("signupRequest", new SignupRequest());
        return "auth/signup";
    }

    /**
     * 회원가입을 처리하고 로그인 화면으로 보낸다(PRG). 검증 실패 시 비밀번호를 비운 채 가입 화면을 다시 렌더링한다.
     *
     * @param request            회원가입 요청
     * @param bindingResult      검증 결과
     * @param redirectAttributes 로그인 화면에 보여줄 메시지와 아이디
     * @return 로그인 화면 리다이렉트 또는 회원가입 뷰 이름
     */
    @PostMapping(SecurityConfig.SIGNUP_PATH)
    public String signup(@Valid @ModelAttribute("signupRequest") SignupRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            memberService.validate(request, bindingResult);
        }
        if (!bindingResult.hasErrors()) {
            try {
                memberService.signup(request);
            } catch (DuplicateUsernameException e) {
                bindingResult.rejectValue("username", "duplicate", e.getMessage());
            }
        }
        if (bindingResult.hasErrors()) {
            request.setPassword(null);
            request.setPasswordConfirm(null);
            return "auth/signup";
        }
        redirectAttributes.addFlashAttribute("toast", "가입이 완료됐어요. 로그인해주세요.");
        redirectAttributes.addFlashAttribute("username", request.getUsername());
        return "redirect:" + SecurityConfig.LOGIN_PATH;
    }
}
