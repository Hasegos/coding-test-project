package io.dev.coding_test.llm.dto;

import io.dev.coding_test.model.enums.LlmProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConnectionTest {

    @Test
    void IP와_포트로_기본_주소를_만든다() {
        LlmConnection connection = new LlmConnection(LlmProvider.LMSTUDIO, "100.100.0.99", 1234, "model", null);

        assertThat(connection.baseUrl()).isEqualTo("http://100.100.0.99:1234");
        assertThat(connection.hasApiKey()).isFalse();
    }

    @Test
    void 문자열로_남길_때_API_Key를_가린다() {
        LlmConnection connection = new LlmConnection(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "model", "secret-token");

        assertThat(connection.toString()).doesNotContain("secret-token").contains("apiKey=****");
    }
}
