package io.dev.coding_test.common.validation.validator;

import io.dev.coding_test.common.validation.annotation.LlmPort;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * {@link LlmPort} 검증기. 판단은 {@link LlmHostGuard}에 맡기고, 거부 사유를 그대로 검증 메시지로 사용한다.
 * <p>
 * 빈 값·범위(1~65535)는 {@code @NotNull} / {@code @Min} / {@code @Max}가 따로 검증하므로 통과시킨다.
 * </p>
 */
@RequiredArgsConstructor
public class LlmPortValidator implements ConstraintValidator<LlmPort, Integer> {

    private final LlmHostGuard llmHostGuard;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null || value < 1 || value > 65535) {
            return true;
        }
        Optional<String> reason = llmHostGuard.rejectPortReason(value);
        if (reason.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(reason.get()).addConstraintViolation();
        return false;
    }
}
