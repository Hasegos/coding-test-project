package io.dev.coding_test.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로컬 LLM 서버 주소로 사용할 수 있는 IP(사설망 · Tailscale 대역)인지 검증한다.
 * <p>
 * 거부 사유(루프백, 링크 로컬, 공인 IP, 도메인 등)에 따라 안내 메시지가 달라지며,
 * {@link #message()}는 사유를 알 수 없을 때만 사용한다.
 * </p>
 *
 * @see io.dev.coding_test.llm.guard.LlmHostGuard#rejectReason(String)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LocalIpValidator.class)
public @interface LocalIp {

    String message() default "로컬 IP(10.x, 172.16~31.x, 192.168.x, Tailscale 100.64~127.x)만 입력할 수 있어요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
