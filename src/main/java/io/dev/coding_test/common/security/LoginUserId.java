package io.dev.coding_test.common.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 로그인한 회원 ID({@link CustomUserPrincipal#getUserId()})를 주입한다.
 * <pre>{@code
 * public MemoResponse get(@LoginUserId Long userId, @PathVariable Long memoId)
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "userId")
public @interface LoginUserId {
}
