package io.dev.coding_test.llm.guard;

import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.enums.IpCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmHostGuardTest {

    private final LlmHostGuard guard = new LlmHostGuard("10.0.0.5");

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.10", "10.1.2.3", "172.20.0.1", "100.66.180.73", "fd7a:115c:a1e0::1", " 192.168.0.10 "})
    void 사설망과_Tailscale_주소는_허용한다(String host) {
        assertThat(guard.rejectReason(host)).isEmpty();
        assertThatCode(() -> guard.check(host)).doesNotThrowAnyException();
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
    })
    void 거부_사유별_메시지를_돌려준다(String host, String reason) {
        String expected = switch (reason) {
            case "LOCALHOST" -> LlmHostGuard.LOCALHOST_MESSAGE;
            case "BLOCKED" -> LlmHostGuard.BLOCKED_MESSAGE;
            case "PUBLIC" -> LlmHostGuard.PUBLIC_MESSAGE;
            case "DATABASE" -> LlmHostGuard.DATABASE_MESSAGE;
            case "DOMAIN" -> LlmHostGuard.DOMAIN_MESSAGE;
            default -> LlmHostGuard.INVALID_IP_MESSAGE;
        };

        assertThat(guard.rejectReason(host)).hasValue(expected);
        assertThatThrownBy(() -> guard.check(host)).isInstanceOf(LlmException.class).hasMessage(expected);
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
}
