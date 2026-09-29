package io.dev.coding_test.llm.queue;

import io.dev.coding_test.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmServerBreakerTest {

    private static final String SERVER = "100.100.0.99:1234";

    private final MutableClock clock = new MutableClock();
    /** 3번 연속 실패하면 5분 휴식, 실패할 때마다 두 배, 최대 30분 */
    private final LlmServerBreaker breaker = new LlmServerBreaker(
            new LlmBreakerProperties(3, Duration.ofMinutes(5), Duration.ofMinutes(30)), clock);

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            breaker.recordFailure(SERVER);
        }
    }

    @Test
    void 연속_실패가_기준에_못_미치면_쉬지_않는다() {
        assertThat(breaker.recordFailure(SERVER)).isEmpty();
        assertThat(breaker.recordFailure(SERVER)).isEmpty();
        assertThat(breaker.openFor(SERVER)).isEmpty();
    }

    @Test
    void 연속_3번_실패하면_5분_쉰다() {
        failTimes(2);

        assertThat(breaker.recordFailure(SERVER)).contains(Duration.ofMinutes(5));
        assertThat(breaker.openFor(SERVER)).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(2));
        assertThat(breaker.openFor(SERVER)).contains(Duration.ofMinutes(3));
    }

    @Test
    void 정상_응답을_받으면_실패_기록을_모두_지운다() {
        failTimes(2);
        breaker.recordSuccess(SERVER);

        assertThat(breaker.recordFailure(SERVER)).isEmpty();
        assertThat(breaker.recordFailure(SERVER)).isEmpty();
    }

    @Test
    void 쉬는_시간이_끝나면_1건만_시험하고_실패하면_두_배로_다시_쉰다() {
        failTimes(3);
        clock.advance(Duration.ofMinutes(5));

        assertThat(breaker.openFor(SERVER)).as("시험 요청 허용").isEmpty();
        assertThat(breaker.recordFailure(SERVER)).as("시험이 실패하면 바로 다시 쉼").contains(Duration.ofMinutes(10));
        assertThat(breaker.openFor(SERVER)).contains(Duration.ofMinutes(10));
    }

    @Test
    void 쉬는_시간은_최대값을_넘지_않는다() {
        failTimes(3);
        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofHours(1));
            breaker.openFor(SERVER);
            breaker.recordFailure(SERVER);
        }

        assertThat(breaker.openFor(SERVER)).hasValueSatisfying(remaining ->
                assertThat(remaining).isLessThanOrEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void 시험이_성공하면_정상으로_돌아오고_다음_휴식은_다시_5분부터_시작한다() {
        failTimes(3);
        clock.advance(Duration.ofMinutes(5));
        breaker.openFor(SERVER);
        breaker.recordFailure(SERVER);
        clock.advance(Duration.ofMinutes(10));
        breaker.openFor(SERVER);

        breaker.recordSuccess(SERVER);
        failTimes(2);

        assertThat(breaker.recordFailure(SERVER)).contains(Duration.ofMinutes(5));
    }

    @Test
    void 회원이_설정을_저장하면_쉬는_중이어도_시험_1건을_허용하고_쉰_횟수는_유지한다() {
        failTimes(3);

        breaker.allowTrial(SERVER);

        assertThat(breaker.openFor(SERVER)).isEmpty();
        assertThat(breaker.recordFailure(SERVER)).as("시험이 실패하면 다음 단계(10분)로 쉼").contains(Duration.ofMinutes(10));
    }

    @Test
    void 쉬는_중이_아닌_서버에_시험을_허용해도_실패_기록은_그대로다() {
        failTimes(2);

        breaker.allowTrial(SERVER);

        assertThat(breaker.recordFailure(SERVER)).as("3번째 실패").isPresent();
    }

    @Test
    void 서버별로_따로_센다() {
        failTimes(3);

        assertThat(breaker.openFor(SERVER)).isPresent();
        assertThat(breaker.openFor("100.66.180.74:1234")).isEmpty();
    }

    @Test
    void 안내_메시지는_남은_시간을_분_단위로_올림한다() {
        assertThat(LlmServerBreaker.message(Duration.ofSeconds(61))).contains("2분 뒤에");
        assertThat(LlmServerBreaker.message(Duration.ZERO)).contains("1분 뒤에").contains("LLM 설정에서 저장하면");
    }

    @Test
    void 설정을_비우면_기본값으로_채운다() {
        LlmBreakerProperties defaults = new LlmBreakerProperties(0, null, null);

        assertThat(defaults.failureThreshold()).isEqualTo(3);
        assertThat(defaults.cooldown()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.maxCooldown()).isEqualTo(Duration.ofHours(1));
    }
}
