package io.dev.coding_test.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPatternTest {

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "User.Name+tag@sub.example.co.kr", "a_b-c%d@my-domain.io", " user@example.com "})
    void 이메일_형식_아이디를_허용한다(String username) {
        assertThat(AuthPattern.isValidUsername(username)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"user", "user@", "@example.com", "user@example", "user@example.c", "us er@example.com",
            "user..name@example.com", "user@exa_mple.com", "유저@example.com", "user@example.com<script>"})
    void 이메일_형식이_아니면_거부한다(String username) {
        assertThat(AuthPattern.isValidUsername(username)).isFalse();
    }

    @Test
    void 아이디는_100자까지_허용한다() {
        String domain = "@example.com";
        assertThat(AuthPattern.isValidUsername("a".repeat(100 - domain.length()) + domain)).isTrue();
        assertThat(AuthPattern.isValidUsername("a".repeat(101 - domain.length()) + domain)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret12!", "Abcdef1@", "p@ssw0rd", "1a~~~~~~", "Z9#Z9#Z9#Z9#"})
    void 영문_숫자_특수문자를_모두_포함한_8자_이상_비밀번호를_허용한다(String password) {
        assertThat(AuthPattern.isValidPassword(password)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"secret12", "secret!!", "12345678!", "se12!", "secret 12!", "비밀번호12!ab", "secret12!\t"})
    void 영문_숫자_특수문자_중_하나라도_없거나_공백_한글이_있으면_거부한다(String password) {
        assertThat(AuthPattern.isValidPassword(password)).isFalse();
    }

    @Test
    void 비밀번호는_8자부터_64자까지_허용한다() {
        assertThat(AuthPattern.isValidPassword("a1!" + "x".repeat(4))).isFalse();
        assertThat(AuthPattern.isValidPassword("a1!" + "x".repeat(5))).isTrue();
        assertThat(AuthPattern.isValidPassword("a1!" + "x".repeat(61))).isTrue();
        assertThat(AuthPattern.isValidPassword("a1!" + "x".repeat(62))).isFalse();
    }

    @Test
    void 아이디는_앞뒤_공백을_지우고_소문자로_정규화한다() {
        assertThat(AuthPattern.normalizeUsername("  User@Example.COM ")).isEqualTo("user@example.com");
        assertThat(AuthPattern.normalizeUsername(null)).isEmpty();
    }
}
