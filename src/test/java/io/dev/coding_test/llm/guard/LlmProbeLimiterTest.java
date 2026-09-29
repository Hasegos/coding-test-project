package io.dev.coding_test.llm.guard;

import io.dev.coding_test.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProbeLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LlmProbeLimiter limiter =
            new LlmProbeLimiter(new LlmGuardProperties(List.of(), List.of(), 3, Duration.ofMinutes(1)), clock);

    @Test
    void 시간_안에_허용_횟수를_넘으면_남은_시간을_돌려준다() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(1L)).isEmpty();
        }
        clock.advance(Duration.ofSeconds(20));

        assertThat(limiter.tryAcquire(1L)).contains(Duration.ofSeconds(40));
    }

    @Test
    void 시간이_지나면_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(1L);
        }
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(1L)).isEmpty();
    }

    @Test
    void 회원마다_따로_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(1L);
        }

        assertThat(limiter.tryAcquire(1L)).isPresent();
        assertThat(limiter.tryAcquire(2L)).isEmpty();
    }

    @Test
    void 안내_메시지는_남은_시간을_초_단위로_올림한다() {
        assertThat(LlmProbeLimiter.message(Duration.ofMillis(40_100))).isEqualTo("LLM 서버 연결 시도가 너무 많아요. 41초 후 다시 시도해주세요.");
        assertThat(LlmProbeLimiter.message(Duration.ZERO)).contains("1초 후");
    }
}
