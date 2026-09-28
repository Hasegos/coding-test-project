package io.dev.coding_test.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로그인 시도 제한 설정 ({@code app.login-attempt.*}).
 * <p>
 * 비밀번호가 틀린 경우만 실패로 센다. 형식이 틀린 입력은 DB를 조회하지 않아 비밀번호를 알아내는 데 쓸 수 없으므로 세지 않는다.
 * </p>
 *
 * @param maxFailures   같은 IP·아이디 조합의 실패 허용 횟수, 도달하면 잠근다
 * @param ipMaxFailures 같은 IP의 실패 허용 횟수 (아이디를 바꿔 가며 시도하는 경우), 도달하면 그 IP의 로그인을 모두 잠근다
 * @param lockDuration  잠금 시간, 마지막 실패 후 이 시간이 지나면 실패 횟수도 초기화한다
 */
@ConfigurationProperties(prefix = "app.login-attempt")
public record LoginAttemptProperties(int maxFailures,
                                     int ipMaxFailures,
                                     Duration lockDuration) {

    public LoginAttemptProperties {
        if (maxFailures < 1) maxFailures = 5;
        if (ipMaxFailures < 1) ipMaxFailures = 20;
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) lockDuration = Duration.ofMinutes(5);
    }
}
