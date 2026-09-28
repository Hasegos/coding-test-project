package io.dev.coding_test.common.config;

import io.dev.coding_test.common.handler.SecurityAccessDeniedHandler;
import io.dev.coding_test.common.handler.SecurityAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 보안 설정.
 * <ul>
 *     <li><b>인증</b>: 세션 기반 폼 로그인. 로그인·회원가입 화면과 정적 리소스를 뺀 모든 요청은 로그인이 필요하다.
 *         로그인하지 않은 API 요청은 401 JSON, 화면 요청은 로그인 화면으로 보낸다.</li>
 *     <li><b>비밀번호</b>: BCrypt 해시로만 저장한다. ({@code {bcrypt}} 접두어가 붙는 위임 인코더)</li>
 *     <li><b>세션</b>: 로그인 성공 시 세션 ID를 새로 발급해 세션 고정 공격을 막고, 로그아웃은 POST(CSRF 토큰 필요)로만 받는다.</li>
 *     <li><b>CSRF</b>: 모든 변경 요청(POST/PUT/DELETE)에 토큰을 요구한다.
 *         화면 폼은 Thymeleaf가 {@code _csrf} 값을 자동으로 넣고, JS 요청은 {@code common.js}가 헤더로 보낸다.</li>
 *     <li><b>보안 헤더</b>: CSP(외부 스크립트·인라인 스크립트 차단), {@code X-Content-Type-Options},
 *         {@code X-Frame-Options}/{@code frame-ancestors}(클릭재킹 방지), {@code Referrer-Policy}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    public static final String LOGIN_PATH = "/login";
    public static final String SIGNUP_PATH = "/signup";

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
                                                   SecurityAccessDeniedHandler accessDeniedHandler,
                                                   SecurityAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PATH, SIGNUP_PATH, SecurityAccessDeniedHandler.ERROR_PAGE_PATH, "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage(LOGIN_PATH)
                        .loginProcessingUrl(LOGIN_PATH)
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/memos")
                        .failureUrl(LOGIN_PATH + "?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(LOGIN_PATH + "?logout")
                        .deleteCookies("JSESSIONID"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    /**
     * 비밀번호 인코더. 기본 BCrypt로 해시하고 {@code {bcrypt}} 접두어를 붙여, 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
