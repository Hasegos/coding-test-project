package io.dev.coding_test.common.validation.validator;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.common.validation.annotation.ValidUsername;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidUsername} 검증기. 빈 값은 {@code @NotBlank}가 따로 검증하므로 통과시킨다.
 */
public class UsernameValidator implements ConstraintValidator<ValidUsername, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || AuthPattern.isValidUsername(value);
    }
}
