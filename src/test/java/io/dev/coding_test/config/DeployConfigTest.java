package io.dev.coding_test.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 환경변수로 바꿀 수 있는 서버 설정의 기본값을 검증한다.
 * 기본값은 HTTP 로 직접 접속하는 개발 환경에서 그대로 동작해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeployConfigTest {

    @Autowired
    private Environment environment;

    @Test
    void 환경변수를_주지_않으면_HTTP_직접_접속_기준의_기본값을_쓴다() {
        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
    }

    @Test
    void 세션_쿠키의_HttpOnly와_SameSite_설정은_유지한다() {
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    }
}
