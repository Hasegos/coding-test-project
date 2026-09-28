package io.dev.coding_test.security.config;

import io.dev.coding_test.security.handler.CustomAuthFailureHandler;
import io.dev.coding_test.security.handler.SecurityAccessDeniedHandler;
import io.dev.coding_test.security.handler.SecurityAuthenticationEntryPoint;
import io.dev.coding_test.security.provider.LoginAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 보안 설정.
 * <ul>
 *     <li><b>인증</b>: 세션 기반 폼 로그인. 로그인·회원가입 화면과 정적 리소스를 뺀 모든 요청은 로그인이 필요하다.
 *         로그인하지 않은 API 요청은 401 JSON, 화면 요청은 로그인 화면으로 보낸다.</li>
 *     <li><b>로그인 검증</b>: {@link LoginAuthenticationProvider}가 아이디(이메일)·비밀번호 형식을 먼저 검사하고
 *         회원을 조회한다. 실패 원인은 {@link CustomAuthFailureHandler}가 로그인 화면에 안내한다.</li>
 *     <li><b>비밀번호</b>: BCrypt 해시로만 저장한다. ({@link PasswordConfig})</li>
 *     <li><b>세션</b>: 로그인 성공 시 세션 ID를 새로 발급해 세션 고정 공격을 막고, 로그아웃은 POST(CSRF 토큰 필요)로만 받는다.</li>
 *     <li><b>CSRF</b>: 모든 변경 요청(POST/PUT/DELETE)에 토큰을 요구한다.
 *         화면 폼은 Thymeleaf가 {@code _csrf} 값을 자동으로 넣고, JS 요청은 {@code common.js}가 헤더로 보낸다.</li>
 *     <li><b>보안 헤더</b>: CSP(외부 스크립트·인라인 스크립트 차단), {@code X-Content-Type-Options},
 *         {@code X-Frame-Options}/{@code frame-ancestors}(클릭재킹 방지), {@code Referrer-Policy}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
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
                                                   SecurityAuthenticationEntryPoint authenticationEntryPoint,
                                                   CustomAuthFailureHandler authFailureHandler) throws Exception {
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
                        .failureHandler(authFailureHandler))
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
}
