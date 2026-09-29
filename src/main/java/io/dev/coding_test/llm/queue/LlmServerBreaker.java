package io.dev.coding_test.llm.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 응답하지 않는 LLM 서버를 잠시 쉬게 하는 차단기.
 * <ul>
 *     <li>서버가 연속으로 {@code failure-threshold}번 응답하지 않으면(연결 실패·시간 초과) 서버를 쉬게 한다.
 *         쉬는 동안 그 서버의 요약은 서버에 요청하지 않고 바로 실패 처리하므로 처리 스레드를 붙잡지 않는다.</li>
 *     <li>쉬는 시간이 지나면 1건만 시험 삼아 요청한다. 실패하면 쉬는 시간을 두 배로 늘려 다시 쉬고, 성공하면 정상으로 돌아온다.</li>
 *     <li>회원이 LLM 설정을 저장하면 쉬는 중이어도 1건은 바로 시험한다. ({@link #allowTrial})
 *         서버를 켠 회원이 오래 기다리지 않게 하되, 계속 실패하면 쉬는 시간은 그대로 늘어난다.</li>
 * </ul>
 * 서버 한 대 기준 메모리에 저장하며, 오래 쓰이지 않은 서버부터 지워 {@link #MAX_ENTRIES}개를 넘지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmServerBreaker {

    static final int MAX_ENTRIES = 1_000;

    private final LlmBreakerProperties properties;
    private final Clock clock;

    private final Map<String, State> states = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * 서버가 쉬는 중인지 확인한다. 쉬는 시간이 지났으면 시험 요청 1건을 허용하는 상태로 바꾼다.
     *
     * @param serverKey LLM 서버 식별값 ({@code host:port})
     * @return 쉬는 중이면 남은 시간, 아니면 {@code Optional.empty()}
     */
    public Optional<Duration> openFor(String serverKey) {
        Instant now = clock.instant();
        synchronized (states) {
            State state = states.get(serverKey);
            if (state == null || state.openUntil == null) {
                return Optional.empty();
            }
            if (now.isBefore(state.openUntil)) {
                return Optional.of(Duration.between(now, state.openUntil));
            }
            // 쉬는 시간이 끝남 — 시험 요청 1건: 실패하면 바로 다시 쉬게 한다.
            state.openUntil = null;
            state.failures = properties.failureThreshold() - 1;
            return Optional.empty();
        }
    }

    /**
     * 서버가 응답하지 않은 것을 기록한다.
     *
     * @param serverKey LLM 서버 식별값
     * @return 이번 실패로 서버가 쉬게 됐으면 쉬는 시간, 아니면 {@code Optional.empty()}
     */
    public Optional<Duration> recordFailure(String serverKey) {
        Instant now = clock.instant();
        synchronized (states) {
            State state = states.computeIfAbsent(serverKey, key -> new State());
            state.failures++;
            if (state.failures < properties.failureThreshold()) {
                return Optional.empty();
            }
            Duration cooldown = cooldown(state.opens);
            state.opens++;
            state.failures = 0;
            state.openUntil = now.plus(cooldown);
            log.warn("LLM 서버 휴식 - server: {}, {}번째, {}분", serverKey, state.opens, cooldown.toMinutes());
            return Optional.of(cooldown);
        }
    }

    /**
     * 서버가 정상 응답한 것을 기록하고 실패 기록을 모두 지운다.
     *
     * @param serverKey LLM 서버 식별값
     */
    public void recordSuccess(String serverKey) {
        synchronized (states) {
            states.remove(serverKey);
        }
    }

    /**
     * 쉬는 중인 서버에 시험 요청 1건을 허용한다. (회원이 LLM 설정을 저장했을 때)
     * 쉰 횟수는 유지하므로 시험이 실패하면 쉬는 시간이 다음 단계로 늘어난다.
     *
     * @param serverKey LLM 서버 식별값
     */
    public void allowTrial(String serverKey) {
        synchronized (states) {
            State state = states.get(serverKey);
            if (state != null && state.openUntil != null) {
                state.openUntil = null;
                state.failures = properties.failureThreshold() - 1;
            }
        }
    }

    /**
     * 모든 기록을 지운다. (테스트용)
     */
    public void clear() {
        synchronized (states) {
            states.clear();
        }
    }

    /**
     * 서버가 쉬는 중이라 요청하지 않았다는 안내 메시지를 만든다.
     *
     * @param remaining 남은 쉬는 시간
     * @return 안내 메시지
     */
    public static String message(Duration remaining) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return "LLM 서버가 연속으로 응답하지 않아 잠시 쉬고 있어요. " + minutes
                + "분 뒤에 다시 시도해주세요. 서버를 켠 뒤 LLM 설정에서 저장하면 바로 다시 시도해요.";
    }

    private Duration cooldown(int opens) {
        Duration cooldown = properties.cooldown().multipliedBy(1L << Math.min(opens, 20));
        return cooldown.compareTo(properties.maxCooldown()) > 0 ? properties.maxCooldown() : cooldown;
    }

    private static final class State {
        private int failures;
        private int opens;
        private Instant openUntil;
    }
}
