package io.dev.coding_test.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원가입 요청 (화면 폼 바인딩).
 * <p>
 * 비밀번호 확인 일치와 아이디 중복은 {@code UserService}가 검사한다.
 * 비밀번호는 BCrypt 입력 한도(72바이트) 안에 들도록 영문·숫자·특수문자(ASCII)로 제한한다.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"password", "passwordConfirm"})
public class SignupRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·밑줄(_) 4~20자로 입력해주세요.")
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해주세요.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]*$",
            message = "비밀번호는 영문과 숫자를 포함하고, 공백·한글 없이 입력해주세요.")
    private String password;

    @NotBlank(message = "비밀번호를 한 번 더 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,12}$", message = "닉네임은 한글·영문·숫자·밑줄(_) 2~12자로 입력해주세요.")
    private String nickname;
}
