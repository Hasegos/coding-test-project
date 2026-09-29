package io.dev.coding_test.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * LLM 서버 주소 허용 판단을 위한 IP 범주.
 */
@Getter
@RequiredArgsConstructor
public enum IpCategory {

    /** 서버 자기 자신 (127.0.0.0/8, ::1, 0.0.0.0, ::) — 항상 거부 */
    LOOPBACK("루프백"),

    /** 링크 로컬(169.254.0.0/16 · 클라우드 메타데이터), 멀티캐스트, 예약·문서용 대역 — 항상 거부 */
    BLOCKED("차단 대역"),

    /** 사설망(10/8, 172.16/12, 192.168/16), Tailscale(100.64/10), IPv6 ULA(fc00::/7) — 허용 */
    PRIVATE("사설망"),

    /** 공인 IP — 로컬 전용이므로 거부 */
    PUBLIC("공인 IP");

    /** 화면·로그에 표시할 범주명 */
    private final String label;
}
