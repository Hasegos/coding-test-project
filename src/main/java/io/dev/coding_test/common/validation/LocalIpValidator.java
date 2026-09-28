package io.dev.coding_test.common.validation;

import io.dev.coding_test.common.util.LocalNetworkUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link LocalIp} 검증기. 빈 값은 {@code @NotBlank}가 따로 검증하므로 통과시킨다.
 */
public class LocalIpValidator implements ConstraintValidator<LocalIp, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return LocalNetworkUtil.isAllowedLocalIp(value.strip());
    }
}
