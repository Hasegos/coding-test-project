package io.dev.coding_test.llm.guard;

import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.enums.IpCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmHostGuardTest {

    private final LlmHostGuard guard = new LlmHostGuard("10.0.0.5");

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.10", "10.1.2.3", "172.20.0.1", "100.66.180.73", "fd7a:115c:a1e0::1", " 192.168.0.10 "})
    void 사설망과_Tailscale_주소는_허용한다(String host) {
        assertThat(guard.rejectReason(host)).isEmpty();
        assertThatCode(() -> guard.check(host, 11434)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "localhost              | LOCALHOST",
            "LOCALHOST              | LOCALHOST",
            "ollama.localhost       | LOCALHOST",
            "host.docker.internal   | LOCALHOST",
            "127.0.0.1              | LOCALHOST",
            "127.8.8.8              | LOCALHOST",
            "0.0.0.0                | LOCALHOST",
            "::1                    | LOCALHOST",
            "::ffff:127.0.0.1       | LOCALHOST",
            "169.254.169.254        | BLOCKED",
            "224.0.0.1              | BLOCKED",
            "192.0.2.1              | BLOCKED",
            "fe80::1                | BLOCKED",
            "8.8.8.8                | PUBLIC",
            "2606:4700:4700::1111   | PUBLIC",
            "10.0.0.5               | DATABASE",
            "evil.example.com       | DOMAIN",
            "ollama                 | DOMAIN",
            "127.1                  | INVALID",
            "2130706433             | INVALID",
            "010.0.0.1              | INVALID",
            "1::2::3                | INVALID",
            "fe80::1%eth0           | INVALID",
            "http://100.66.180.73   | URL",
            "http://100.66.180.73:1234 | URL",
            "https://192.168.0.10/  | URL",
            "100.66.180.73:1234     | URL",
            "192.168.0.10/          | URL",
            "[fd7a:115c:a1e0::1]    | URL",
            "http://localhost:1234  | URL",
    })
    void 거부_사유별_메시지를_돌려준다(String host, String reason) {
        String expected = switch (reason) {
            case "LOCALHOST" -> LlmHostGuard.LOCALHOST_MESSAGE;
            case "BLOCKED" -> LlmHostGuard.BLOCKED_MESSAGE;
            case "PUBLIC" -> LlmHostGuard.PUBLIC_MESSAGE;
            case "DATABASE" -> LlmHostGuard.DATABASE_MESSAGE;
            case "DOMAIN" -> LlmHostGuard.DOMAIN_MESSAGE;
            case "URL" -> LlmHostGuard.URL_MESSAGE;
            default -> LlmHostGuard.INVALID_IP_MESSAGE;
        };

        assertThat(guard.rejectReason(host)).hasValue(expected);
        assertThatThrownBy(() -> guard.check(host, 11434)).isInstanceOf(LlmException.class).hasMessage(expected);
    }

    @Test
    void 빈_값은_형식_오류로_거부한다() {
        assertThat(guard.rejectReason(null)).hasValue(LlmHostGuard.INVALID_IP_MESSAGE);
        assertThat(guard.rejectReason("  ")).hasValue(LlmHostGuard.INVALID_IP_MESSAGE);
    }

    @Test
    void 데이터베이스_호스트가_사설_IP면_그_주소를_거부한다() {
        LlmHostGuard dbGuard = new LlmHostGuard("192.168.0.20");

        assertThat(dbGuard.rejectReason("192.168.0.20")).hasValue(LlmHostGuard.DATABASE_MESSAGE);
        assertThat(dbGuard.rejectReason("192.168.0.21")).isEmpty();
    }

    @Test
    void 주소_범주를_알려준다() {
        assertThat(guard.categoryOf("100.66.180.73")).hasValue(IpCategory.PRIVATE);
        assertThat(guard.categoryOf("evil.example.com")).isEmpty();
    }

    // ===================== 허용 대역·포트 (llm.guard) =====================

    private final LlmHostGuard restricted = new LlmHostGuard("10.0.0.5",
            new LlmGuardProperties(List.of("100.64.0.0/10", "192.168.0.10"), List.of(1234, 11434), 0, null, null));

    @ParameterizedTest
    @ValueSource(strings = {"100.66.180.73", "100.127.255.254", "192.168.0.10"})
    void 허용_대역을_설정하면_그_대역의_주소만_허용한다(String host) {
        assertThat(restricted.rejectReason(host)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.1", "192.168.0.11", "10.0.0.1", "172.16.0.1", "fd7a:115c:a1e0::1"})
    void 허용_대역_밖의_사설망_주소는_허용_대역을_안내하며_거부한다(String host) {
        assertThat(restricted.rejectReason(host))
                .contains(LlmHostGuard.NETWORK_MESSAGE_PREFIX + "100.64.0.0/10, 192.168.0.10/32");
    }

    @Test
    void 허용_대역을_설정해도_루프백_공인_IP는_원래_사유로_거부한다() {
        assertThat(restricted.rejectReason("127.0.0.1")).contains(LlmHostGuard.LOCALHOST_MESSAGE);
        assertThat(restricted.rejectReason("8.8.8.8")).contains(LlmHostGuard.PUBLIC_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(ints = {80, 22, 5432, 8080, 443})
    void 허용_포트가_아니면_허용_포트를_안내하며_거부한다(int port) {
        assertThat(restricted.rejectPortReason(port)).contains(LlmHostGuard.PORT_MESSAGE_PREFIX + "1234, 11434");
        assertThatThrownBy(() -> restricted.check("100.66.180.73", port)).isInstanceOf(LlmException.class)
                .hasMessage(LlmHostGuard.PORT_MESSAGE_PREFIX + "1234, 11434");
    }

    @Test
    void 허용_포트와_대역_안의_주소는_통과한다() {
        assertThatCode(() -> restricted.check("100.66.180.73", 1234)).doesNotThrowAnyException();
        assertThat(restricted.rejectPortReason(11434)).isEmpty();
    }

    @Test
    void 허용_포트를_비우면_모든_포트를_허용한다() {
        assertThat(guard.rejectPortReason(8080)).isEmpty();
    }

    @Test
    void 허용_대역_형식이_틀리면_기동을_중단한다() {
        assertThatThrownBy(() -> new LlmHostGuard("", new LlmGuardProperties(List.of("100.64.0.0/33"), List.of(), 0, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===================== 기본 설정: Tailscale 전용 =====================

    /** application.yml 기본값과 같은 설정 (허용 대역을 지정하지 않으면 Tailscale 대역) */
    private final LlmHostGuard tailscaleOnly = new LlmHostGuard("10.0.0.5",
            new LlmGuardProperties(null, null, 0, null, null));

    @Test
    void 허용_대역을_지정하지_않으면_Tailscale_대역만_허용한다() {
        assertThat(tailscaleOnly.isTailscaleOnly()).isTrue();
        assertThat(restricted.isTailscaleOnly()).isFalse();
        assertThat(guard.isTailscaleOnly()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.66.180.73", "100.64.0.1", "fd7a:115c:a1e0::1"})
    void Tailscale_전용이면_Tailscale_주소를_허용한다(String host) {
        assertThat(tailscaleOnly.rejectReason(host)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.10", "10.1.2.3", "172.20.0.1", "fd00::1", "127.0.0.1", "localhost", "8.8.8.8", "61.72.10.20"})
    void Tailscale_전용이면_루프백_공인_사설망_주소를_연결_가이드_안내로_거부한다(String host) {
        assertThat(tailscaleOnly.rejectReason(host)).contains(LlmHostGuard.TAILSCALE_ONLY_MESSAGE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "169.254.169.254   | 사용할 수 없는 주소예요.",
            "http://100.66.1.1 | http:// 나 포트 없이 IP만 입력해주세요.",
            "evil.example.com  | 도메인이 아닌 IP 주소를 입력해주세요.",
    })
    void Tailscale_전용이어도_형식_오류와_차단_대역은_원래_사유로_안내한다(String host, String messageStart) {
        assertThat(tailscaleOnly.rejectReason(host)).hasValueSatisfying(reason -> assertThat(reason).startsWith(messageStart));
    }
}
