package io.dev.coding_test.common.config;

import io.dev.coding_test.common.handler.SecurityAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 보안 설정.
 * <p>
 * 로그인 기능은 없으므로 모든 요청을 허용하고, Spring Security는 다음 용도로만 사용한다.
 * </p>
 * <ul>
 *     <li><b>CSRF</b>: 모든 변경 요청(POST/PUT/DELETE)에 토큰을 요구한다.
 *         화면 폼은 Thymeleaf가 {@code _csrf} 값을 자동으로 넣고, JS 요청은 {@code common.js}가 헤더로 보낸다.</li>
 *     <li><b>보안 헤더</b>: CSP(외부 스크립트·인라인 스크립트 차단), {@code X-Content-Type-Options},
 *         {@code X-Frame-Options}/{@code frame-ancestors}(클릭재킹 방지), {@code Referrer-Policy}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /**
     * Content-Security-Policy.
     * 스크립트는 같은 출처의 파일만 허용하고(인라인 스크립트 금지), 폰트/스타일은 Pretendard CDN만 추가로 허용한다.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' https://cdn.jsdelivr.net",
            "font-src 'self' https://cdn.jsdelivr.net",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
