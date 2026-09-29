package io.dev.coding_test.common.validation.annotation;

import io.dev.coding_test.common.validation.validator.LlmPortValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * LLM 서버 포트로 사용할 수 있는지({@code llm.guard.allowed-ports}) 검증한다.
 * <p>
 * 거부 메시지에 허용 포트 목록이 들어가며, {@link #message()}는 사유를 알 수 없을 때만 사용한다.
 * </p>
 *
 * @see io.dev.coding_test.llm.guard.LlmHostGuard#rejectPortReason(Integer)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LlmPortValidator.class)
public @interface LlmPort {

    String message() default "이 서비스에서 허용하지 않은 포트예요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
