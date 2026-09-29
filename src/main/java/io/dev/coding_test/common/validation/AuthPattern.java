package io.dev.coding_test.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 로그인 · 회원가입 입력값(아이디 · 비밀번호) 형식 규칙.
 * <p>
 * 회원가입 검증({@code @ValidUsername}, {@code @ValidPassword})과 로그인 검증({@code LoginAuthenticationProvider})이
 * 같은 규칙을 쓰도록 한곳에 둔다. 화면 검증({@code static/js/auth.js})도 같은 정규식을 사용한다.
 * </p>
 * <ul>
 *     <li>아이디: 이메일 형식, 100자 이하, 대소문자 구분 없음(소문자로 저장)</li>
 *     <li>비밀번호: 영문 · 숫자 · 특수문자를 모두 포함한 8~64자, 공백 · 한글 불가 (BCrypt 입력 한도 72바이트 이내)</li>
 * </ul>
 */
public final class AuthPattern {

    public static final int USERNAME_MAX_LENGTH = 100;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 64;

    /** 이메일: 로컬파트@도메인.최상위도메인(2자 이상), 연속된 점 불가 */
    public static final Pattern USERNAME = Pattern.compile(
            "^(?!.*\\.\\.)[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    /** 영문 · 숫자 · 특수문자(공백 제외 ASCII 기호)를 각각 하나 이상 포함한 8~64자 */
    public static final Pattern PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{" + PASSWORD_MIN_LENGTH + "," + PASSWORD_MAX_LENGTH + "}$");

    public static final String USERNAME_MESSAGE = "아이디는 이메일 형식(예: user@example.com)으로 100자 이하로 입력해주세요.";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해주세요. (공백·한글 불가)";

    private AuthPattern() {
    }

    /**
     * 아이디가 이메일 형식인지 확인한다. (앞뒤 공백은 무시)
     *
     * @param username 아이디
     * @return 형식에 맞으면 {@code true}
     */
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        String value = username.strip();
        return value.length() <= USERNAME_MAX_LENGTH && USERNAME.matcher(value).matches();
    }

    /**
     * 비밀번호가 형식에 맞는지 확인한다. (공백을 허용하지 않으므로 앞뒤 공백도 그대로 검사)
     *
     * @param password 비밀번호
     * @return 형식에 맞으면 {@code true}
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    /**
     * 아이디(이메일)를 저장·조회용으로 정규화한다. (앞뒤 공백 제거, 소문자)
     *
     * @param username 아이디
     * @return 정규화한 아이디
     */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
