package io.dev.coding_test.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 문자열 컬럼을 {@link SecretCipher}로 암호화해서 저장하고, 읽을 때 복호화하는 JPA 컨버터.
 * <p>
 * 엔티티 필드에 {@code @Convert(converter = EncryptedStringConverter.class)}로 지정한다.
 * Spring이 컨버터를 만들어 주므로 암호화 키를 주입받는다.
 * </p>
 * <ul>
 *     <li>암호화 이전에 평문으로 저장된 값(접두어 없음)은 그대로 읽고, 다음 저장 때 암호화된다.</li>
 *     <li>복호화에 실패하면(키 변경·변조) 값을 비운 것으로 보고 오류를 기록한다.
 *         화면에는 "저장된 API Key 없음"으로 보이며, API Key를 다시 입력하면 새 키로 암호화된다.</li>
 * </ul>
 */
@Slf4j
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final SecretCipher secretCipher;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : secretCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!SecretCipher.isEncrypted(dbData)) {
            log.warn("암호화되지 않은 값을 읽었습니다. 다음 저장 때 암호화됩니다.");
            return dbData;
        }
        try {
            return secretCipher.decrypt(dbData);
        } catch (SecretDecryptionException e) {
            log.error("저장된 값을 복호화하지 못했습니다. API_KEY_ENCRYPTION_KEY가 바뀌었다면 API Key를 다시 입력해주세요. ({})",
                    e.getMessage());
            return null;
        }
    }
}
