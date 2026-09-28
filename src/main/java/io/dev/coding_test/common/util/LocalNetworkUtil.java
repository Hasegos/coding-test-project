package io.dev.coding_test.common.util;

import java.util.regex.Pattern;

/**
 * 로컬 LLM 서버 주소로 허용할 IP 대역 판단 유틸리티.
 * <p>
 * 사용자가 입력한 주소로 서버가 직접 HTTP 요청을 보내므로(SSRF 위험), 다음만 허용한다.
 * </p>
 * <ul>
 *     <li>IPv4 숫자 주소만 허용 — 호스트명은 DNS 결과가 바뀔 수 있어(DNS rebinding) 거부</li>
 *     <li>허용 대역: 127.0.0.0/8(루프백), 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16(사설망),
 *         100.64.0.0/10(Tailscale · CGNAT)</li>
 *     <li>그 외(공인 IP, 0.0.0.0, 169.254.0.0/16 링크 로컬 · 클라우드 메타데이터 등)는 거부</li>
 * </ul>
 */
public final class LocalNetworkUtil {

    /** 0~255 옥텟 4개 (앞자리 0 금지: 010 같은 8진수 해석 차단) */
    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private LocalNetworkUtil() {
    }

    /**
     * 로컬 LLM 서버 주소로 허용하는 IPv4 주소인지 확인한다.
     *
     * @param host 사용자가 입력한 주소
     * @return 루프백 · 사설망 · Tailscale 대역의 IPv4 주소면 {@code true}
     */
    public static boolean isAllowedLocalIp(String host) {
        if (host == null || !IPV4.matcher(host).matches()) {
            return false;
        }
        String[] parts = host.split("\\.");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);

        return a == 127                                  // 127.0.0.0/8
                || a == 10                               // 10.0.0.0/8
                || (a == 172 && b >= 16 && b <= 31)      // 172.16.0.0/12
                || (a == 192 && b == 168)                // 192.168.0.0/16
                || (a == 100 && b >= 64 && b <= 127);    // 100.64.0.0/10 (Tailscale)
    }
}
