package io.dev.coding_test.common.validation.validator;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.common.validation.annotation.ValidPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidPassword} 검증기. 빈 값은 {@code @NotBlank}가 따로 검증하므로 통과시킨다.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isEmpty() || AuthPattern.isValidPassword(value);
    }
}
