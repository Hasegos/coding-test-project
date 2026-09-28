package io.dev.coding_test.dto.auth;

import io.dev.coding_test.common.validation.annotation.ValidPassword;
import io.dev.coding_test.common.validation.annotation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원가입 요청 (화면 폼 바인딩).
 * <p>
 * 아이디(이메일)·비밀번호 형식은 {@link io.dev.coding_test.common.validation.AuthPattern} 규칙으로 검증하고,
 * 비밀번호 확인 일치와 아이디 중복은 {@code UserService}가 검사한다.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"password", "passwordConfirm"})
public class SignupRequest {

    @NotBlank(message = "아이디(이메일)를 입력해주세요.")
    @ValidUsername
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @ValidPassword
    private String password;

    @NotBlank(message = "비밀번호를 한 번 더 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,12}$", message = "닉네임은 한글·영문·숫자·밑줄(_) 2~12자로 입력해주세요.")
    private String nickname;
}
