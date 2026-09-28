package io.dev.coding_test.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalNetworkUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.255.255.254",          // 루프백
            "10.0.0.1", "10.255.255.255",            // 10.0.0.0/8
            "172.16.0.1", "172.31.255.255",          // 172.16.0.0/12
            "192.168.0.10", "192.168.255.1",         // 192.168.0.0/16
            "100.64.0.1", "100.66.180.73", "100.127.255.255" // Tailscale 100.64.0.0/10
    })
    void 루프백_사설망_Tailscale_대역은_허용한다(String host) {
        assertThat(LocalNetworkUtil.isAllowedLocalIp(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "1.1.1.1",                    // 공인 IP
            "169.254.169.254", "169.254.0.1",        // 링크 로컬 · 클라우드 메타데이터
            "0.0.0.0", "255.255.255.255",
            "172.15.0.1", "172.32.0.1",              // 172.16/12 경계 밖
            "100.63.255.255", "100.128.0.1",         // 100.64/10 경계 밖
            "192.169.0.1",
            "localhost", "llm.internal", "example.com", // 호스트명 (DNS rebinding 방지)
            "010.0.0.1", "127.0.0.01",               // 앞자리 0 (8진수 해석 차단)
            "127.1", "256.0.0.1", "10.0.0.1:1234", "10.0.0.1/", " 10.0.0.1", "::1", "http://10.0.0.1"
    })
    void 그_외_주소는_거부한다(String host) {
        assertThat(LocalNetworkUtil.isAllowedLocalIp(host)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 빈_값은_거부한다(String host) {
        assertThat(LocalNetworkUtil.isAllowedLocalIp(host)).isFalse();
    }
}
