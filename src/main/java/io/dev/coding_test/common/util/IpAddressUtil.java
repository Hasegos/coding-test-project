package io.dev.coding_test.common.util;

import io.dev.coding_test.model.enums.IpCategory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * IP 주소 파싱·분류 유틸리티.
 * <p>
 * DNS 조회 없이 IP 문자열만 해석하고, 허용 판단용 범주({@link IpCategory})로 분류한다.
 * IPv6 안에 IPv4가 들어간 주소(::ffff:a.b.c.d, 6to4 2002::/16)는 안쪽 IPv4 기준으로 판단한다.
 * </p>
 */
public final class IpAddressUtil {

    /** 0~255 옥텟 4개 (앞자리 0 금지: 010 같은 8진수 해석 차단) */
    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    /** IPv6 문자열에 쓰이는 문자 (zone id '%' 등은 제외) */
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpAddressUtil() {
    }

    /**
     * IP 문자열을 해석한다. 도메인·비표준 표기({@code 127.1}, {@code 2130706433}, {@code 010.0.0.1})는 해석하지 않는다.
     *
     * @param host 입력값
     * @return IP 주소, IP 표기가 아니면 {@code Optional.empty()}
     */
    public static Optional<InetAddress> parseLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (IPV4.matcher(host).matches()) {
                String[] parts = host.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    bytes[i] = (byte) Integer.parseInt(parts[i]);
                }
                return Optional.of(InetAddress.getByAddress(bytes));
            }
            // ':'가 있으면 JDK가 IPv6 리터럴로만 해석하고 DNS 조회를 하지 않는다.
            if (host.indexOf(':') >= 0 && IPV6_CHARS.matcher(host).matches()) {
                return Optional.of(InetAddress.getByName(host));
            }
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * IP를 허용 판단용 범주로 분류한다.
     *
     * @param address IP 주소
     * @return IP 범주
     */
    public static IpCategory classify(InetAddress address) {
        byte[] b = address.getAddress();

        // 6to4(2002::/16): 2~5번째 바이트에 IPv4가 들어 있다.
        if (address instanceof Inet6Address && u(b[0]) == 0x20 && u(b[1]) == 0x02) {
            return classifyIpv4(u(b[2]), u(b[3]), u(b[4]), u(b[5]));
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
            return IpCategory.LOOPBACK;
        }
        if (address instanceof Inet4Address) {
            return classifyIpv4(u(b[0]), u(b[1]), u(b[2]), u(b[3]));
        }
        return classifyIpv6(address, b);
    }

    private static IpCategory classifyIpv4(int a, int b, int c, int d) {
        if (a == 127 || (a == 0 && b == 0 && c == 0 && d == 0)) {
            return IpCategory.LOOPBACK;
        }
        if (a == 0                                     // 0.0.0.0/8 "this network"
                || (a == 169 && b == 254)              // 169.254.0.0/16 링크 로컬 · 클라우드 메타데이터
                || a >= 224                            // 224/4 멀티캐스트, 240/4 예약, 255.255.255.255
                || (a == 192 && b == 0 && c == 0)      // 192.0.0.0/24 IETF 예약
                || (a == 192 && b == 0 && c == 2)      // 192.0.2.0/24 문서용 (TEST-NET-1)
                || (a == 198 && (b == 18 || b == 19))  // 198.18.0.0/15 벤치마크
                || (a == 198 && b == 51 && c == 100)   // 198.51.100.0/24 문서용 (TEST-NET-2)
                || (a == 203 && b == 0 && c == 113)) { // 203.0.113.0/24 문서용 (TEST-NET-3)
            return IpCategory.BLOCKED;
        }
        if (a == 10                                    // 10.0.0.0/8
                || (a == 172 && b >= 16 && b <= 31)    // 172.16.0.0/12
                || (a == 192 && b == 168)              // 192.168.0.0/16
                || (a == 100 && b >= 64 && b <= 127)) { // 100.64.0.0/10 (Tailscale · CGNAT)
            return IpCategory.PRIVATE;
        }
        return IpCategory.PUBLIC;
    }

    private static IpCategory classifyIpv6(InetAddress address, byte[] b) {
        if (address.isLinkLocalAddress() || address.isMulticastAddress() || address.isSiteLocalAddress()) {
            return IpCategory.BLOCKED;                 // fe80::/10, ff00::/8, fec0::/10
        }
        if ((u(b[0]) & 0xfe) == 0xfc) {
            return IpCategory.PRIVATE;                 // fc00::/7 ULA (Tailscale fd7a:115c:a1e0::/48 포함)
        }
        if (u(b[0]) == 0x20 && u(b[1]) == 0x01 && (u(b[2]) == 0x0d && u(b[3]) == 0xb8)) {
            return IpCategory.BLOCKED;                 // 2001:db8::/32 문서용
        }
        if (u(b[0]) == 0x20 && u(b[1]) == 0x01 && u(b[2]) == 0x00 && u(b[3]) == 0x00) {
            return IpCategory.BLOCKED;                 // 2001::/32 Teredo (IPv4 우회 터널)
        }
        if ((u(b[0]) & 0xe0) == 0x20) {
            return IpCategory.PUBLIC;                  // 2000::/3 글로벌 유니캐스트
        }
        return IpCategory.BLOCKED;                     // 그 외 예약 대역 (64:ff9b:: NAT64, IPv4 호환 주소 등)
    }

    private static int u(byte value) {
        return value & 0xff;
    }
}
