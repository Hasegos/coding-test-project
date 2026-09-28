package io.dev.coding_test.common.validation;

import io.dev.coding_test.llm.guard.LlmHostGuard;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * {@link LocalIp} 검증기. 판단은 {@link LlmHostGuard}에 맡기고, 거부 사유를 그대로 검증 메시지로 사용한다.
 * <p>
 * 빈 값은 {@code @NotBlank}가 따로 검증하므로 통과시킨다.
 * </p>
 */
@RequiredArgsConstructor
public class LocalIpValidator implements ConstraintValidator<LocalIp, String> {

    private final LlmHostGuard llmHostGuard;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        Optional<String> reason = llmHostGuard.rejectReason(value);
        if (reason.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(escape(reason.get())).addConstraintViolation();
        return false;
    }

    /** 메시지 템플릿 문법({@code {}}, {@code ${}})으로 해석되지 않도록 이스케이프한다. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
