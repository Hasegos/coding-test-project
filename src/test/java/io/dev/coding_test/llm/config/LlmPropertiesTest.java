package io.dev.coding_test.llm.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    @Test
    void 누락된_값은_기본값으로_채운다() {
        LlmProperties properties = new LlmProperties(0.2, null, null, null, null, 0, 0, 0);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.concurrency()).isEqualTo(1);
        assertThat(properties.queueCapacity()).isEqualTo(100);
        assertThat(properties.maxParallel()).isEqualTo(8);
    }
}
