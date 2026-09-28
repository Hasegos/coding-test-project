package io.dev.coding_test.security.attempt;

import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.security.config.LoginAttemptProperties;
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
 * 로그인 실패 횟수를 세고, 반복되면 잠시 로그인을 막는다. (비밀번호 무차별 대입 방지)
 * <ul>
 *     <li>같은 IP·아이디: {@code max-failures}회 실패하면 잠근다. 다른 IP에서는 로그인할 수 있어
 *         누군가 남의 아이디로 일부러 틀려도 본인은 계속 로그인할 수 있다.</li>
 *     <li>같은 IP: 아이디를 바꿔 가며 {@code ip-max-failures}회 실패하면 그 IP의 로그인을 모두 잠근다.</li>
 *     <li>로그인에 성공하면 그 IP·아이디의 실패 횟수를 지운다. IP 실패 횟수는 지우지 않는다.
 *         (자기 계정으로 한 번씩 로그인해 횟수를 초기화하는 우회 방지)</li>
 * </ul>
 * <p>
 * 서버 한 대 기준 메모리에 저장하며, 오래 쓰이지 않은 항목부터 지워 {@link #MAX_ENTRIES}개를 넘지 않는다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    /** 기억할 최대 IP·아이디 조합 수 (임의의 아이디를 계속 보내 메모리를 소모시키는 것 방지) */
    static final int MAX_ENTRIES = 10_000;

    private final LoginAttemptProperties properties;
    private final Clock clock;

    private final Map<String, Attempt> attempts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Attempt> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * 로그인이 잠겨 있는지 확인한다.
     *
     * @param ip       요청 IP
     * @param username 입력한 아이디
     * @return 잠겨 있으면 남은 잠금 시간, 아니면 {@code Optional.empty()}
     */
    public Optional<Duration> lockedFor(String ip, String username) {
        Instant now = clock.instant();
        synchronized (attempts) {
            Duration remaining = max(remaining(attempts.get(ipKey(ip)), now),
                    remaining(attempts.get(userKey(ip, username)), now));
            return remaining.isPositive() ? Optional.of(remaining) : Optional.empty();
        }
    }

    /**
     * 비밀번호가 틀린 로그인을 기록한다.
     *
     * @param ip       요청 IP
     * @param username 입력한 아이디
     * @return 이번 실패로 잠겼으면 잠금 시간, 아니면 {@code Optional.empty()}
     */
    public Optional<Duration> recordFailure(String ip, String username) {
        Instant now = clock.instant();
        synchronized (attempts) {
            boolean userLocked = fail(userKey(ip, username), properties.maxFailures(), now);
            boolean ipLocked = fail(ipKey(ip), properties.ipMaxFailures(), now);
            if (userLocked || ipLocked) {
                log.warn("로그인 시도 제한 - ip: {}, 대상: {}", ip, ipLocked ? "IP 전체" : "IP·아이디");
                return Optional.of(properties.lockDuration());
            }
            return Optional.empty();
        }
    }

    /**
     * 로그인 성공 시 그 IP·아이디의 실패 횟수를 지운다.
     *
     * @param ip       요청 IP
     * @param username 입력한 아이디
     */
    public void recordSuccess(String ip, String username) {
        synchronized (attempts) {
            attempts.remove(userKey(ip, username));
        }
    }

    /**
     * 실패 횟수를 1 늘리고, 허용 횟수에 도달하면 잠근다.
     *
     * @return 이번 실패로 잠겼으면 {@code true}
     */
    private boolean fail(String key, int limit, Instant now) {
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        if (attempt.lastFailure != null
                && !now.isBefore(attempt.lastFailure.plus(properties.lockDuration()))) {
            attempt.failures = 0;
        }
        attempt.failures++;
        attempt.lastFailure = now;
        if (attempt.failures >= limit) {
            attempt.failures = 0;
            attempt.lockedUntil = now.plus(properties.lockDuration());
            return true;
        }
        return false;
    }

    private static Duration remaining(Attempt attempt, Instant now) {
        if (attempt == null || attempt.lockedUntil == null || !now.isBefore(attempt.lockedUntil)) {
            return Duration.ZERO;
        }
        return Duration.between(now, attempt.lockedUntil);
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String userKey(String ip, String username) {
        return "user:" + ip + "|" + AuthPattern.normalizeUsername(username);
    }

    private static final class Attempt {
        private int failures;
        private Instant lastFailure;
        private Instant lockedUntil;
    }
}
