package io.dev.coding_test.common.crypto;

import io.dev.coding_test.dto.setting.LlmSettingRequest;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM API Key가 DB에는 암호문으로 저장되고, 애플리케이션에서는 원문으로 읽히는지 검증한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class EncryptedStringConverterTest {

    @Autowired
    private LlmSettingService llmSettingService;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = testUsers.create("tester").getUserId();
    }

    @Test
    void API_Key는_DB에_암호문으로_저장하고_읽을_때_복호화한다() {
        save("sk-lm-secret-token");

        String stored = storedApiKey();
        assertThat(stored).startsWith(SecretCipher.PREFIX).doesNotContain("sk-lm-secret-token");
        assertThat(llmSettingService.findConnection(userId)).get()
                .satisfies(connection -> assertThat(connection.apiKey()).isEqualTo("sk-lm-secret-token"));
    }

    @Test
    void 암호화_이전에_평문으로_저장된_값은_그대로_읽고_다시_저장하면_암호화한다() {
        save("temp");
        jdbcTemplate.update("UPDATE llm_setting SET api_key = ? WHERE user_id = ?", "legacy-plain-key", userId);
        entityManager.clear();

        assertThat(llmSettingService.findConnection(userId)).get()
                .satisfies(connection -> assertThat(connection.apiKey()).isEqualTo("legacy-plain-key"));

        llmSettingService.save(userId, request(null));
        entityManager.flush();
        assertThat(storedApiKey()).startsWith(SecretCipher.PREFIX);
        entityManager.clear();
        assertThat(llmSettingService.findConnection(userId)).get()
                .satisfies(connection -> assertThat(connection.apiKey()).isEqualTo("legacy-plain-key"));
    }

    @Test
    void 다른_키로_암호화된_값은_저장된_키가_없는_것으로_본다() {
        save("temp");
        String otherKey = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
        jdbcTemplate.update("UPDATE llm_setting SET api_key = ? WHERE user_id = ?",
                new SecretCipher(otherKey).encrypt("other-key-secret"), userId);
        entityManager.clear();

        assertThat(llmSettingService.getSetting(userId)).get()
                .satisfies(setting -> assertThat(setting.hasApiKey()).isFalse());
    }

    private void save(String apiKey) {
        llmSettingService.save(userId, request(apiKey));
        entityManager.flush();
    }

    private static LlmSettingRequest request(String apiKey) {
        return new LlmSettingRequest(LlmProvider.LMSTUDIO, "100.100.0.99", 1234, "qwen2.5-7b-instruct", apiKey, false);
    }

    private String storedApiKey() {
        return jdbcTemplate.queryForObject("SELECT api_key FROM llm_setting WHERE user_id = ?", String.class, userId);
    }
}
