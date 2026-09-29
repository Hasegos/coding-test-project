package io.dev.coding_test.llm.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 서버 휴식(연속 실패 차단) 설정 ({@code llm.breaker.*}).
 * <p>
 * 서버가 응답하지 않으면 요약 1건이 처리 스레드를 {@code llm.read-timeout}(기본 120초)만큼 잡는다.
 * 연속으로 실패한 서버는 잠시 쉬게 해, 그 서버의 나머지 요약을 스레드를 잡지 않고 바로 실패 처리한다.
 * 다시 실패하면 쉬는 시간을 두 배씩 늘린다.
 * </p>
 *
 * @param failureThreshold 연속 실패가 이 횟수에 도달하면 서버를 쉬게 한다
 * @param cooldown         처음 쉬는 시간
 * @param maxCooldown      쉬는 시간의 최대값 (다시 실패할 때마다 두 배로 늘어나다 여기서 멈춘다)
 */
@ConfigurationProperties(prefix = "llm.breaker")
public record LlmBreakerProperties(int failureThreshold, Duration cooldown, Duration maxCooldown) {

    public LlmBreakerProperties {
        if (failureThreshold < 1) failureThreshold = 3;
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) cooldown = Duration.ofMinutes(5);
        if (maxCooldown == null || maxCooldown.compareTo(cooldown) < 0) maxCooldown = Duration.ofHours(1).compareTo(cooldown) >= 0
                ? Duration.ofHours(1) : cooldown;
    }
}
