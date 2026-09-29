package io.dev.coding_test.llm.guard;

import io.dev.coding_test.common.util.IpAddressUtil;

import java.net.InetAddress;

/**
 * CIDR 표기 IP 대역 (예: {@code 100.64.0.0/10}, {@code fd7a:115c:a1e0::/48}).
 *
 * @param network 대역의 시작 주소
 * @param prefix  고정 비트 수
 */
public record IpRange(InetAddress network, int prefix) {

    /**
     * CIDR 문자열을 해석한다. 접두 길이를 생략하면 주소 하나(/32, /128)로 본다.
     *
     * @param cidr CIDR 문자열
     * @return IP 대역
     * @throws IllegalArgumentException 형식이 올바르지 않은 경우
     */
    public static IpRange parse(String cidr) {
        String value = cidr == null ? "" : cidr.strip();
        int slash = value.indexOf('/');
        String ip = slash < 0 ? value : value.substring(0, slash);
        InetAddress network = IpAddressUtil.parseLiteral(ip)
                .orElseThrow(() -> new IllegalArgumentException("IP 대역 형식이 올바르지 않아요: " + cidr));
        int bits = network.getAddress().length * 8;
        int prefix;
        try {
            prefix = slash < 0 ? bits : Integer.parseInt(value.substring(slash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("IP 대역 형식이 올바르지 않아요: " + cidr, e);
        }
        if (prefix < 0 || prefix > bits) {
            throw new IllegalArgumentException("IP 대역 형식이 올바르지 않아요: " + cidr);
        }
        return new IpRange(network, prefix);
    }

    /**
     * 주소가 이 대역에 속하는지 확인한다. IPv4와 IPv6는 서로 다른 대역으로 본다.
     *
     * @param address 확인할 주소
     * @return 대역에 속하면 {@code true}
     */
    public boolean contains(InetAddress address) {
        byte[] target = address.getAddress();
        byte[] base = network.getAddress();
        if (target.length != base.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (target[i] != base[i]) {
                return false;
            }
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (target[fullBytes] & mask) == (base[fullBytes] & mask);
    }

    @Override
    public String toString() {
        return network.getHostAddress() + "/" + prefix;
    }
}
