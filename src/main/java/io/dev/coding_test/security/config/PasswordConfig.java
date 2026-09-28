package io.dev.coding_test.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱 설정.
 */
@Configuration
public class PasswordConfig {

    /**
     * 비밀번호 인코더. 기본 BCrypt로 해시하고 {@code {bcrypt}} 접두어를 붙여, 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다.
     *
     * @return BCrypt 기반 위임 PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
