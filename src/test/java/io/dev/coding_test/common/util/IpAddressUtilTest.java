package io.dev.coding_test.common.util;

import io.dev.coding_test.model.enums.IpCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressUtilTest {

    @ParameterizedTest
    @CsvSource({
            // 루프백 · 미지정 주소
            "127.0.0.1, LOOPBACK", "127.255.255.254, LOOPBACK", "0.0.0.0, LOOPBACK",
            "::1, LOOPBACK", "::, LOOPBACK", "::ffff:127.0.0.1, LOOPBACK", "2002:7f00:1::, LOOPBACK",
            // 링크 로컬(클라우드 메타데이터) · 멀티캐스트 · 예약 · 문서용
            "169.254.169.254, BLOCKED", "169.254.0.1, BLOCKED", "0.1.2.3, BLOCKED", "224.0.0.1, BLOCKED",
            "239.255.255.250, BLOCKED", "240.0.0.1, BLOCKED", "255.255.255.255, BLOCKED", "192.0.0.1, BLOCKED",
            "192.0.2.2, BLOCKED", "198.18.0.1, BLOCKED", "198.19.255.255, BLOCKED", "198.51.100.7, BLOCKED",
            "203.0.113.9, BLOCKED", "fe80::1, BLOCKED", "ff02::1, BLOCKED", "fec0::1, BLOCKED",
            "2001:db8::1, BLOCKED", "2001:0:4136:e378::1, BLOCKED", "64:ff9b::a00:1, BLOCKED",
            "::ffff:169.254.169.254, BLOCKED", "2002:a9fe:a9fe::, BLOCKED",
            // 사설망 · Tailscale(CGNAT) · IPv6 ULA
            "10.0.0.1, PRIVATE", "10.255.255.255, PRIVATE", "172.16.0.1, PRIVATE", "172.31.255.255, PRIVATE",
            "192.168.0.10, PRIVATE", "100.64.0.1, PRIVATE", "100.66.180.73, PRIVATE", "100.127.255.255, PRIVATE",
            "fd7a:115c:a1e0::1, PRIVATE", "fc00::1, PRIVATE", "::ffff:192.168.0.10, PRIVATE", "2002:c0a8:000a::, PRIVATE",
            // 공인 IP (경계값 포함)
            "8.8.8.8, PUBLIC", "172.15.255.255, PUBLIC", "172.32.0.1, PUBLIC", "100.63.255.255, PUBLIC",
            "100.128.0.1, PUBLIC", "192.169.0.1, PUBLIC", "169.253.0.1, PUBLIC", "2606:4700:4700::1111, PUBLIC",
    })
    void IP를_범주로_분류한다(String ip, IpCategory expected) {
        InetAddress address = IpAddressUtil.parseLiteral(ip).orElseThrow();

        assertThat(IpAddressUtil.classify(address)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "localhost", "example.com", "192.168.0", "192.168.0.1.5", "256.1.1.1", "010.0.0.1", "127.1",
            "2130706433", "0x7f.0.0.1", "192.168.0.1 ", " 192.168.0.1", "1::2::3", "fe80::1%eth0", "[::1]", "::g"
    })
    void 표준_IP_표기가_아니면_해석하지_않는다(String host) {
        assertThat(IpAddressUtil.parseLiteral(host)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.10", "::1", "FD7A:115C:A1E0::1", "::ffff:10.0.0.1"})
    void 표준_IP_표기는_DNS_조회_없이_해석한다(String host) {
        assertThat(IpAddressUtil.parseLiteral(host)).isPresent();
    }
}
