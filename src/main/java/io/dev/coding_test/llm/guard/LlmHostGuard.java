package io.dev.coding_test.llm.guard;

import io.dev.coding_test.common.util.IpAddressUtil;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.enums.IpCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM 서버 주소 검사 (SSRF 방지).
 * <p>
 * 서버가 사용자가 입력한 주소로 직접 요청을 보내므로, 로컬 LLM 서버로 쓸 수 있는 주소만 통과시킨다.
 * LLM 설정을 저장할 때와 실제로 호출할 때 모두 이 검사를 거친다.
 * </p>
 * <ul>
 *     <li>IP 숫자 주소만 허용 — 도메인은 DNS 결과가 바뀔 수 있어(DNS rebinding) 거부</li>
 *     <li>localhost·루프백(127.x, ::1) 거부 — 로컬 LLM은 사설망 IP나 Tailscale IP로 연결한다</li>
 *     <li>링크 로컬(169.254.x · 클라우드 메타데이터), 멀티캐스트, 예약·문서용 대역 거부</li>
 *     <li>공인 IP 거부 (로컬 전용), 데이터베이스 서버 주소 거부</li>
 *     <li>허용: 사설망(10/8, 172.16/12, 192.168/16), Tailscale(100.64/10), IPv6 ULA(fc00::/7)</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmHostGuard {

    public static final String LOCALHOST_MESSAGE =
            "localhost·127.0.0.1 같은 루프백 주소는 사용할 수 없어요. LLM 서버 PC의 사설 IP(192.168.x.x 등)나 Tailscale IP(100.x.x.x)를 입력해주세요.";
    public static final String URL_MESSAGE =
            "http:// 나 포트 없이 IP만 입력해주세요. (예: 100.66.180.73) 포트는 오른쪽 칸에 입력합니다.";
    public static final String DOMAIN_MESSAGE = "도메인이 아닌 IP 주소를 입력해주세요. (예: Tailscale IP 100.x.x.x)";
    public static final String INVALID_IP_MESSAGE = "IP 주소 형식이 올바르지 않아요.";
    public static final String BLOCKED_MESSAGE =
            "사용할 수 없는 주소예요. (링크 로컬 169.254.x.x, 멀티캐스트, 예약·문서용 대역)";
    public static final String PUBLIC_MESSAGE =
            "공인 IP는 사용할 수 없어요. 사설망(10.x, 172.16~31.x, 192.168.x) 또는 Tailscale(100.64~127.x) IP를 입력해주세요.";
    public static final String DATABASE_MESSAGE = "데이터베이스 서버 주소는 LLM 서버로 사용할 수 없어요.";

    /** 루프백으로 연결되는 로컬 호스트명 (Docker 호스트 포함) */
    private static final Set<String> LOCAL_HOSTNAMES = Set.of(
            "localhost", "host.docker.internal", "gateway.docker.internal", "kubernetes.docker.internal");

    /** 주소 전체(http://…, IP:포트, [IPv6])를 붙여넣은 경우 — LM Studio의 "Reachable at" 값을 그대로 복사하는 경우가 많다 */
    private static final Pattern URL_LIKE = Pattern.compile("://|/|\\[|^[0-9.]+:\\d*$");

    /** IP처럼 보이지만 해석되지 않는 값(127.1, 2130706433, 1::2::3)은 형식 오류로 안내한다 */
    private static final Pattern IP_LIKE = Pattern.compile("^[0-9.]+$|:");

    private final String databaseHost;
    private volatile Set<InetAddress> databaseAddresses;

    public LlmHostGuard(@Value("${POSTGRESQL_HOST:localhost}") String databaseHost) {
        this.databaseHost = databaseHost == null ? "" : databaseHost.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 주소를 LLM 서버로 사용할 수 없는 이유를 반환한다.
     *
     * @param rawHost 사용자가 입력한 주소
     * @return 거부 사유, 사용할 수 있으면 {@code Optional.empty()}
     */
    public Optional<String> rejectReason(String rawHost) {
        String host = rawHost == null ? "" : rawHost.strip().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            return Optional.of(INVALID_IP_MESSAGE);
        }
        if (URL_LIKE.matcher(host).find()) {
            return Optional.of(URL_MESSAGE);
        }
        if (LOCAL_HOSTNAMES.contains(host) || host.endsWith(".localhost")) {
            return Optional.of(LOCALHOST_MESSAGE);
        }

        Optional<InetAddress> parsed = IpAddressUtil.parseLiteral(host);
        if (parsed.isEmpty()) {
            return Optional.of(IP_LIKE.matcher(host).find() ? INVALID_IP_MESSAGE : DOMAIN_MESSAGE);
        }

        InetAddress address = parsed.get();
        return switch (IpAddressUtil.classify(address)) {
            case LOOPBACK -> Optional.of(LOCALHOST_MESSAGE);
            case BLOCKED -> Optional.of(BLOCKED_MESSAGE);
            case PUBLIC -> Optional.of(PUBLIC_MESSAGE);
            case PRIVATE -> host.equals(databaseHost) || databaseAddresses().contains(address)
                    ? Optional.of(DATABASE_MESSAGE)
                    : Optional.empty();
        };
    }

    /**
     * LLM 서버로 사용할 수 있는 주소인지 검사한다. (호출 직전 재검사용)
     *
     * @param host LLM 서버 주소
     * @throws LlmException 사용할 수 없는 주소인 경우
     */
    public void check(String host) {
        rejectReason(host).ifPresent(reason -> {
            log.warn("LLM 서버 주소 거부 - host: {}, 사유: {}", host, reason);
            throw new LlmException(reason);
        });
    }

    /**
     * 데이터베이스 서버 주소(설정값)의 IP 목록. 설정값이라 DNS 조회를 허용하며, 처음 한 번만 조회한다.
     */
    private Set<InetAddress> databaseAddresses() {
        Set<InetAddress> cached = databaseAddresses;
        if (cached == null) {
            try {
                cached = databaseHost.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(InetAddress.getAllByName(databaseHost)));
            } catch (UnknownHostException e) {
                cached = Set.of();
            }
            databaseAddresses = cached;
        }
        return cached;
    }

    /**
     * 범주 판단 결과 (로그·테스트용).
     *
     * @param host 주소
     * @return IP 범주, IP 표기가 아니면 {@code Optional.empty()}
     */
    public Optional<IpCategory> categoryOf(String host) {
        return IpAddressUtil.parseLiteral(host == null ? "" : host.strip()).map(IpAddressUtil::classify);
    }
}
