package io.dev.coding_test.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 로그인 시도 제한 설정({@link LoginAttemptProperties})과 시각 기준을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptConfig {

    /**
     * 잠금 시각 계산에 쓰는 시계. 테스트에서는 원하는 시각으로 바꾼 시계를 넣는다.
     *
     * @return 시스템 UTC 시계
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
