package io.dev.coding_test.common.validation.annotation;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.common.validation.validator.UsernameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 아이디가 이메일 형식(100자 이하)인지 검증한다. 빈 값은 {@code @NotBlank}가 따로 검증한다.
 *
 * @see AuthPattern#USERNAME
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsernameValidator.class)
@Documented
public @interface ValidUsername {

    String message() default AuthPattern.USERNAME_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
