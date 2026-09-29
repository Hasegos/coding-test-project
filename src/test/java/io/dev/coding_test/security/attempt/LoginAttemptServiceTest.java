package io.dev.coding_test.security.attempt;

import io.dev.coding_test.security.config.LoginAttemptProperties;
import io.dev.coding_test.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.10";
    private static final String EMAIL = "hasegos@example.com";

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service =
            new LoginAttemptService(new LoginAttemptProperties(5, 20, Duration.ofMinutes(5)), clock);

    @Test
    void 같은_IP와_아이디로_5회_틀리면_5분간_잠근다() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.recordFailure(IP, EMAIL)).isEmpty();
        }
        assertThat(service.lockedFor(IP, EMAIL)).isEmpty();

        assertThat(service.recordFailure(IP, EMAIL)).contains(Duration.ofMinutes(5));
        assertThat(service.lockedFor(IP, EMAIL)).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(4));
        assertThat(service.lockedFor(IP, EMAIL)).contains(Duration.ofMinutes(1));

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.lockedFor(IP, EMAIL)).isEmpty();
    }

    @Test
    void 아이디는_대소문자와_앞뒤_공백을_구분하지_않는다() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(IP, i % 2 == 0 ? " HASEGOS@example.com " : EMAIL);
        }

        assertThat(service.lockedFor(IP, EMAIL)).isPresent();
    }

    @Test
    void 다른_IP에서는_같은_아이디로_로그인할_수_있다() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(IP, EMAIL);
        }

        assertThat(service.lockedFor("198.51.100.7", EMAIL)).isEmpty();
    }

    @Test
    void 마지막_실패_후_잠금_시간이_지나면_실패_횟수를_초기화한다() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(IP, EMAIL);
        }
        clock.advance(Duration.ofMinutes(5));

        assertThat(service.recordFailure(IP, EMAIL)).isEmpty();
        assertThat(service.lockedFor(IP, EMAIL)).isEmpty();
    }

    @Test
    void 로그인에_성공하면_IP_아이디_실패_횟수를_지운다() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(IP, EMAIL);
        }
        service.recordSuccess(IP, EMAIL);

        assertThat(service.recordFailure(IP, EMAIL)).isEmpty();
    }

    @Test
    void 같은_IP에서_아이디를_바꿔_가며_20회_틀리면_IP_전체를_잠근다() {
        for (int i = 0; i < 19; i++) {
            assertThat(service.recordFailure(IP, "user" + i + "@example.com")).isEmpty();
        }

        assertThat(service.recordFailure(IP, "user19@example.com")).isPresent();
        assertThat(service.lockedFor(IP, "someone@example.com")).isPresent();
        assertThat(service.lockedFor("198.51.100.7", "someone@example.com")).isEmpty();
    }

    @Test
    void 로그인에_성공해도_IP_실패_횟수는_지우지_않는다() {
        for (int i = 0; i < 19; i++) {
            service.recordFailure(IP, "user" + i + "@example.com");
            service.recordSuccess(IP, "mine@example.com");
        }

        assertThat(service.recordFailure(IP, "user19@example.com")).isPresent();
    }

    @Test
    void 기억하는_항목_수는_최대치를_넘지_않고_오래된_항목부터_지운다() {
        service.recordFailure("198.51.100.7", EMAIL);
        for (int i = 0; i < 4; i++) {
            service.recordFailure("198.51.100.7", EMAIL);
        }
        assertThat(service.lockedFor("198.51.100.7", EMAIL)).isPresent();

        for (int i = 0; i < LoginAttemptService.MAX_ENTRIES; i++) {
            service.recordFailure("10.0." + (i / 250) + "." + (i % 250), EMAIL);
        }

        assertThat(service.lockedFor("198.51.100.7", EMAIL)).isEmpty();
    }
}
