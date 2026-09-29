package io.dev.coding_test.llm.guard;

import io.dev.coding_test.common.util.IpAddressUtil;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.enums.IpCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 *     <li>{@code llm.guard.allowed-networks}의 대역만, {@code llm.guard.allowed-ports}에 있는 포트만 허용
 *         — 서버가 속한 내부망의 다른 장비·포트를 확인하는 데 쓰이지 않도록 범위를 좁힌다.</li>
 *     <li>기본값은 Tailscale 대역만 허용한다. 이때 루프백·공인 IP·다른 사설망 주소는 모두 Tailscale 연결 안내로 거부한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmHostGuard {

    public static final String LOCALHOST_MESSAGE =
            "localhost·127.0.0.1 같은 루프백 주소는 사용할 수 없어요. LLM 서버 PC의 사설 IP(192.168.x.x 등)나 Tailscale IP(100.x.x.x)를 입력해주세요.";
    public static final String URL_MESSAGE =
            "http:// 나 포트 없이 IP만 입력해주세요. (예: 100.x.x.x) 포트는 오른쪽 칸에 입력합니다.";
    public static final String DOMAIN_MESSAGE = "도메인이 아닌 IP 주소를 입력해주세요. (예: Tailscale IP 100.x.x.x)";
    public static final String INVALID_IP_MESSAGE = "IP 주소 형식이 올바르지 않아요.";
    public static final String BLOCKED_MESSAGE =
            "사용할 수 없는 주소예요. (링크 로컬 169.254.x.x, 멀티캐스트, 예약·문서용 대역)";
    public static final String PUBLIC_MESSAGE =
            "공인 IP는 사용할 수 없어요. 사설망(10.x, 172.16~31.x, 192.168.x) 또는 Tailscale(100.64~127.x) IP를 입력해주세요.";
    public static final String DATABASE_MESSAGE = "데이터베이스 서버 주소는 LLM 서버로 사용할 수 없어요.";
    public static final String NETWORK_MESSAGE_PREFIX = "이 서비스에서 허용하지 않은 네트워크 주소예요. 허용 대역: ";
    public static final String PORT_MESSAGE_PREFIX = "이 서비스에서 허용하지 않은 포트예요. 허용 포트: ";
    public static final String TAILSCALE_ONLY_MESSAGE =
            "Tailscale IP(100.x.x.x)만 사용할 수 있어요. LLM 설정 화면의 Tailscale 연결 가이드를 따라 LLM PC를 연결한 뒤 그 PC의 Tailscale IP를 입력해주세요.";

    private static final List<IpRange> TAILSCALE_RANGES =
            LlmGuardProperties.TAILSCALE_NETWORKS.stream().map(IpRange::parse).toList();

    /** 루프백으로 연결되는 로컬 호스트명 (Docker 호스트 포함) */
    private static final Set<String> LOCAL_HOSTNAMES = Set.of(
            "localhost", "host.docker.internal", "gateway.docker.internal", "kubernetes.docker.internal");

    /** 주소 전체(http://…, IP:포트, [IPv6])를 붙여넣은 경우 — LM Studio의 "Reachable at" 값을 그대로 복사하는 경우가 많다 */
    private static final Pattern URL_LIKE = Pattern.compile("://|/|\\[|^[0-9.]+:\\d*$");

    /** IP처럼 보이지만 해석되지 않는 값(127.1, 2130706433, 1::2::3)은 형식 오류로 안내한다 */
    private static final Pattern IP_LIKE = Pattern.compile("^[0-9.]+$|:");

    private final String databaseHost;
    private volatile Set<InetAddress> databaseAddresses;
    private final List<IpRange> allowedNetworks;
    private final Set<Integer> allowedPorts;
    private final boolean tailscaleOnly;

    /**
     * @param databaseHost 데이터베이스 서버 주소 (LLM 서버로 쓰지 못하게 막는다)
     * @param properties   허용 대역·포트 설정, 형식이 틀린 대역이 있으면 기동을 중단한다
     */
    @Autowired
    public LlmHostGuard(@Value("${POSTGRESQL_HOST:localhost}") String databaseHost, LlmGuardProperties properties) {
        this.databaseHost = databaseHost == null ? "" : databaseHost.strip().toLowerCase(Locale.ROOT);
        this.allowedNetworks = properties.allowedNetworks().stream().map(IpRange::parse).toList();
        this.allowedPorts = new TreeSet<>(properties.allowedPorts());
        this.tailscaleOnly = !allowedNetworks.isEmpty() && allowedNetworks.stream()
                .allMatch(range -> TAILSCALE_RANGES.stream().anyMatch(tailscale -> tailscale.containsRange(range)));
        if (!allowedNetworks.isEmpty() || !allowedPorts.isEmpty()) {
            log.info("LLM 서버 허용 범위 - 대역: {}, 포트: {}",
                    allowedNetworks.isEmpty() ? "사설망·Tailscale 전체" : allowedNetworks,
                    allowedPorts.isEmpty() ? "전체" : allowedPorts);
        }
    }

    /**
     * 대역·포트 제한 없이 만든다. (테스트용)
     *
     * @param databaseHost 데이터베이스 서버 주소
     */
    public LlmHostGuard(String databaseHost) {
        this(databaseHost, LlmGuardProperties.unrestricted());
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
            return Optional.of(tailscaleOr(LOCALHOST_MESSAGE));
        }

        Optional<InetAddress> parsed = IpAddressUtil.parseLiteral(host);
        if (parsed.isEmpty()) {
            return Optional.of(IP_LIKE.matcher(host).find() ? INVALID_IP_MESSAGE : DOMAIN_MESSAGE);
        }

        InetAddress address = parsed.get();
        return switch (IpAddressUtil.classify(address)) {
            case LOOPBACK -> Optional.of(tailscaleOr(LOCALHOST_MESSAGE));
            case BLOCKED -> Optional.of(BLOCKED_MESSAGE);
            case PUBLIC -> Optional.of(tailscaleOr(PUBLIC_MESSAGE));
            case PRIVATE -> {
                if (host.equals(databaseHost) || databaseAddresses().contains(address)) {
                    yield Optional.of(DATABASE_MESSAGE);
                }
                if (!allowedNetworks.isEmpty() && allowedNetworks.stream().noneMatch(range -> range.contains(address))) {
                    yield Optional.of(tailscaleOr(NETWORK_MESSAGE_PREFIX + join(allowedNetworks)));
                }
                yield Optional.empty();
            }
        };
    }

    /**
     * 포트를 LLM 서버 포트로 사용할 수 없는 이유를 반환한다.
     *
     * @param port 포트
     * @return 거부 사유, 사용할 수 있으면 {@code Optional.empty()}
     */
    public Optional<String> rejectPortReason(Integer port) {
        if (port == null || allowedPorts.isEmpty() || allowedPorts.contains(port)) {
            return Optional.empty();
        }
        return Optional.of(PORT_MESSAGE_PREFIX + join(allowedPorts));
    }

    /**
     * LLM 서버로 사용할 수 있는 주소·포트인지 검사한다. (호출 직전 재검사용 — 설정을 바꾼 뒤 저장돼 있던 주소도 막는다)
     *
     * @param host LLM 서버 주소
     * @param port LLM 서버 포트
     * @throws LlmException 사용할 수 없는 주소·포트인 경우
     */
    public void check(String host, int port) {
        rejectReason(host).or(() -> rejectPortReason(port)).ifPresent(reason -> {
            log.warn("LLM 서버 주소 거부 - host: {}, port: {}, 사유: {}", host, port, reason);
            throw new LlmException(reason);
        });
    }

    /**
     * Tailscale 대역만 허용하는지 여부. (설정 화면의 안내 문구 선택용)
     *
     * @return Tailscale 대역만 허용하면 {@code true}
     */
    public boolean isTailscaleOnly() {
        return tailscaleOnly;
    }

    /**
     * Tailscale 대역(100.64.0.0/10, fd7a:115c:a1e0::/48)의 IP인지 확인한다.
     *
     * @param host IP 문자열
     * @return Tailscale 주소면 {@code true}
     */
    public static boolean isTailscaleAddress(String host) {
        return IpAddressUtil.parseLiteral(host == null ? "" : host.strip())
                .map(address -> TAILSCALE_RANGES.stream().anyMatch(range -> range.contains(address)))
                .orElse(false);
    }

    /** Tailscale만 허용할 때는 어떤 주소를 넣었든 Tailscale 연결 안내 하나로 답한다. */
    private String tailscaleOr(String message) {
        return tailscaleOnly ? TAILSCALE_ONLY_MESSAGE : message;
    }

    private static String join(Collection<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
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
