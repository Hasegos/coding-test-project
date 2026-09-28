package io.dev.coding_test.common.validation.annotation;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.common.validation.validator.PasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호가 영문 · 숫자 · 특수문자를 모두 포함한 8~64자인지 검증한다. 빈 값은 {@code @NotBlank}가 따로 검증한다.
 *
 * @see AuthPattern#PASSWORD
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
@Documented
public @interface ValidPassword {

    String message() default AuthPattern.PASSWORD_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
