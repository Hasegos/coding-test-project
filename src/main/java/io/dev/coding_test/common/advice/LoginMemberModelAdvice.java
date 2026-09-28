package io.dev.coding_test.common.advice;

import io.dev.coding_test.common.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 화면(Thymeleaf) 요청에 로그인한 회원의 닉네임을 공통으로 전달한다. (헤더 우측 회원 메뉴)
 * <p>
 * 세션에 저장된 principal 값만 읽으므로 DB를 조회하지 않는다.
 * </p>
 */
@ControllerAdvice
public class LoginMemberModelAdvice {

    @ModelAttribute("loginNickname")
    public String loginNickname(@AuthenticationPrincipal LoginMember loginMember) {
        return loginMember == null ? null : loginMember.getNickname();
    }
}
