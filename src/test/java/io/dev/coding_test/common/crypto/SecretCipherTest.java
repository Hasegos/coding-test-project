package io.dev.coding_test.common.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void 암호화한_값을_복호화하면_원문이_나온다() {
        String encrypted = cipher.encrypt("sk-lm-secret-token");

        assertThat(encrypted).startsWith(SecretCipher.PREFIX).doesNotContain("sk-lm-secret-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-lm-secret-token");
    }

    @Test
    void 같은_평문도_매번_다른_암호문이_된다() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void 최대_길이_200자_API_Key도_컬럼_길이_400_안에_들어간다() {
        String encrypted = cipher.encrypt("~".repeat(200));

        assertThat(encrypted.length()).isLessThanOrEqualTo(400);
        assertThat(cipher.decrypt(encrypted)).hasSize(200);
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        String encrypted = new SecretCipher(OTHER_KEY).encrypt("secret");

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(SecretDecryptionException.class)
                .hasMessageContaining("키가 다르거나 변조된");
    }

    @Test
    void IV_암호문_인증_태그_중_한_비트라도_바뀌면_거부한다() {
        byte[] payload = Base64.getUrlDecoder().decode(cipher.encrypt("secret").substring(SecretCipher.PREFIX.length()));
        for (int i = 0; i < payload.length; i++) {
            byte[] tampered = payload.clone();
            tampered[i] ^= 0x01;
            String value = SecretCipher.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered);

            assertThatThrownBy(() -> cipher.decrypt(value)).isInstanceOf(SecretDecryptionException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-text", "v1:", "v1:!!!", "v1:AAAA"})
    void 형식이_잘못된_값은_거부한다(String value) {
        assertThatThrownBy(() -> cipher.decrypt(value)).isInstanceOf(SecretDecryptionException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not-base64!", "c2hvcnQ="})
    void 키가_없거나_32바이트가_아니면_기동하지_않는다(String key) {
        assertThatThrownBy(() -> new SecretCipher(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_ENCRYPTION_KEY")
                .hasMessageContaining("openssl rand -base64 32");
    }
}
