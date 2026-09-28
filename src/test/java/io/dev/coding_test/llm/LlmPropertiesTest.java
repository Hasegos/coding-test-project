package io.dev.coding_test.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    @Test
    void base_url이_없으면_런타임별_기본_주소를_사용한다() {
        assertThat(properties(LlmProvider.OLLAMA, "").resolvedBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties(LlmProvider.LMSTUDIO, null).resolvedBaseUrl()).isEqualTo("http://localhost:1234");
    }

    @Test
    void base_url_끝의_슬래시는_제거한다() {
        assertThat(properties(LlmProvider.OLLAMA, " http://100.64.0.10:11434/ ").resolvedBaseUrl())
                .isEqualTo("http://100.64.0.10:11434");
    }

    @Test
    void 누락된_값은_기본값으로_채운다() {
        LlmProperties properties = new LlmProperties(null, null, null, null, 0.2, null, null, 0, 0);

        assertThat(properties.provider()).isEqualTo(LlmProvider.OLLAMA);
        assertThat(properties.model()).isEqualTo("qwen2.5:7b");
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.concurrency()).isEqualTo(1);
        assertThat(properties.hasApiKey()).isFalse();
    }

    private static LlmProperties properties(LlmProvider provider, String baseUrl) {
        return new LlmProperties(provider, baseUrl, "model", "", 0.2, null, null, 1, 10);
    }
}
