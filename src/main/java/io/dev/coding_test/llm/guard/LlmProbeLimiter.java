package io.dev.coding_test.llm.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 회원별 LLM 서버 연결 시도(연결 테스트, 서버 주소 변경) 횟수 제한.
 * <p>
 * 연결 테스트 결과(연결 실패 / 응답 형식 오류 / 상태 코드)로 내부망의 열린 포트를 하나씩 확인하는 것을 느리게 만든다.
 * {@code llm.guard.probe-window}마다 {@code llm.guard.probe-limit}회까지 허용한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LlmProbeLimiter {

    /** 기억할 최대 회원 수 (오래 쓰이지 않은 회원부터 지운다) */
    static final int MAX_ENTRIES = 10_000;

    public static final String MESSAGE_FORMAT = "LLM 서버 연결 시도가 너무 많아요. %d초 후 다시 시도해주세요.";

    private final LlmGuardProperties properties;
    private final Clock clock;

    private final Map<Long, Window> windows = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Window> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * 연결 시도를 1회 기록한다.
     *
     * @param userId 회원 ID
     * @return 허용하면 {@code Optional.empty()}, 횟수를 넘었으면 다시 시도할 수 있을 때까지 남은 시간
     */
    public Optional<Duration> tryAcquire(Long userId) {
        Instant now = clock.instant();
        synchronized (windows) {
            Window window = windows.get(userId);
            if (window == null || !now.isBefore(window.start.plus(properties.probeWindow()))) {
                window = new Window(now);
                windows.put(userId, window);
            }
            if (window.count >= properties.probeLimit()) {
                return Optional.of(Duration.between(now, window.start.plus(properties.probeWindow())));
            }
            window.count++;
            return Optional.empty();
        }
    }

    /**
     * 횟수 초과 안내 메시지를 만든다.
     *
     * @param retryAfter 남은 시간
     * @return 안내 메시지
     */
    public static String message(Duration retryAfter) {
        return MESSAGE_FORMAT.formatted(Math.max(1, (retryAfter.toMillis() + 999) / 1000));
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }
    }
}
