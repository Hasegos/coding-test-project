package io.dev.coding_test.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 비밀 값(LLM API Key 등)을 DB에 저장하기 위한 대칭키 암호화.
 * <p>
 * API Key는 LLM 서버에 원문 그대로 보내야 하므로 해싱(되돌릴 수 없음)이 아니라 암호화한다.
 * Python {@code cryptography}의 Fernet과 같은 방식으로, 외부에서 주입한 키로 암호화하고 변조를 검출한다.
 * </p>
 * <ul>
 *     <li>알고리즘: AES-256-GCM (암호화 + 무결성 검증), 값마다 12바이트 무작위 IV</li>
 *     <li>저장 형식: {@code v1:} + Base64URL(IV ‖ 암호문 ‖ 인증 태그) — 버전 접두어로 이후 알고리즘·키 교체에 대비</li>
 *     <li>키: 환경변수 {@code API_KEY_ENCRYPTION_KEY}(32바이트 Base64). DB와 분리해 보관하므로 DB가 유출돼도 복호화할 수 없다.</li>
 * </ul>
 */
@Component
public class SecretCipher {

    public static final String PREFIX = "v1:";

    static final String KEY_GUIDE = "API_KEY_ENCRYPTION_KEY 환경변수에 32바이트 Base64 키를 설정해주세요. "
            + "(생성: openssl rand -base64 32)";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.crypto.secret-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    /**
     * 평문을 암호화한다. 같은 평문도 IV가 달라 매번 다른 결과가 나온다.
     *
     * @param plaintext 평문
     * @return {@code v1:} 접두어가 붙은 암호문
     */
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    /**
     * 암호문을 복호화한다.
     *
     * @param ciphertext {@link #encrypt}의 결과
     * @return 평문
     * @throws SecretDecryptionException 형식이 올바르지 않거나, 키가 다르거나, 변조된 경우
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new SecretDecryptionException("암호문 형식이 올바르지 않습니다.");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length < IV_LENGTH + TAG_BITS / 8) {
                throw new SecretDecryptionException("암호문 길이가 올바르지 않습니다.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new SecretDecryptionException("키가 다르거나 변조된 암호문입니다.", e);
        } catch (IllegalArgumentException e) {
            throw new SecretDecryptionException("암호문 인코딩이 올바르지 않습니다.", e);
        } catch (GeneralSecurityException e) {
            throw new SecretDecryptionException("복호화에 실패했습니다.", e);
        }
    }

    /**
     * 암호화한 값인지 확인한다. (접두어 기준)
     *
     * @param value DB에 저장된 값
     * @return {@code v1:}로 시작하면 {@code true}
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("API Key 암호화 키가 없습니다. " + KEY_GUIDE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("API Key 암호화 키가 Base64 형식이 아닙니다. " + KEY_GUIDE, e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("API Key 암호화 키는 32바이트여야 합니다. (현재 " + bytes.length + "바이트) "
                    + KEY_GUIDE);
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
