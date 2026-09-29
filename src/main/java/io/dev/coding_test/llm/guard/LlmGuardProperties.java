package io.dev.coding_test.llm.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * LLM 서버 주소 제한 설정 ({@code llm.guard.*}).
 * <p>
 * 서버가 회원이 입력한 주소로 직접 요청을 보내므로, 연결 테스트나 요약 실패 메시지로
 * 서버가 속한 내부망(공유기 관리 화면, NAS 등)의 열린 포트를 알아내는 데 쓰이지 않도록 범위를 좁힌다.
 * </p>
 *
 * @param allowedNetworks 허용할 네트워크 대역(CIDR), 비어 있으면 사설망·Tailscale 대역 전체 허용
 *                        (기본: Tailscale만 — LLM PC가 서버와 다른 네트워크(다른 집·학교)에 있어도 연결되는 유일한 방법)
 * @param allowedPorts    허용할 포트, 비어 있으면 전체 허용 (기본: LM Studio 1234, Ollama 11434)
 * @param probeLimit      회원 한 명이 {@code probeWindow} 동안 할 수 있는 연결 테스트·주소 변경 횟수
 * @param probeWindow     연결 시도 횟수를 세는 시간
 * @param shareEmail      회원이 LLM PC를 Tailscale로 공유할 운영자 이메일 (연결 가이드에 표시, 비우면 표시하지 않음)
 */
@ConfigurationProperties(prefix = "llm.guard")
public record LlmGuardProperties(List<String> allowedNetworks,
                                 List<Integer> allowedPorts,
                                 int probeLimit,
                                 Duration probeWindow,
                                 String shareEmail) {

    /** Tailscale 주소 대역 (IPv4 CGNAT 대역, IPv6 ULA 중 Tailscale 전용 대역) */
    public static final List<String> TAILSCALE_NETWORKS = List.of("100.64.0.0/10", "fd7a:115c:a1e0::/48");
    public static final List<Integer> DEFAULT_PORTS = List.of(1234, 11434);

    public LlmGuardProperties {
        allowedNetworks = allowedNetworks == null ? TAILSCALE_NETWORKS : List.copyOf(allowedNetworks);
        allowedPorts = allowedPorts == null ? DEFAULT_PORTS : List.copyOf(allowedPorts);
        if (probeLimit < 1) probeLimit = 10;
        if (probeWindow == null || probeWindow.isNegative() || probeWindow.isZero()) probeWindow = Duration.ofMinutes(1);
        shareEmail = shareEmail == null ? "" : shareEmail.strip();
    }

    /**
     * 대역·포트 제한 없이 사설망·Tailscale 대역 전체를 허용하는 설정. (테스트용)
     *
     * @return 제한 없는 설정
     */
    public static LlmGuardProperties unrestricted() {
        return new LlmGuardProperties(List.of(), List.of(), 0, null, null);
    }
}
