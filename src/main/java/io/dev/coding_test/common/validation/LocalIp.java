package io.dev.coding_test.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로컬 LLM 서버 주소로 허용하는 IPv4(루프백 · 사설망 · Tailscale 대역)인지 검증한다.
 *
 * @see io.dev.coding_test.common.util.LocalNetworkUtil#isAllowedLocalIp(String)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LocalIpValidator.class)
public @interface LocalIp {

    String message() default "로컬 IP(127.x, 10.x, 172.16~31.x, 192.168.x, Tailscale 100.64~127.x)만 입력할 수 있어요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
