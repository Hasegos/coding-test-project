package io.dev.coding_test.common.crypto;

/**
 * 암호문을 복호화할 수 없을 때 발생하는 예외. (형식 오류, 다른 키, 변조)
 */
public class SecretDecryptionException extends RuntimeException {

    public SecretDecryptionException(String message) {
        super(message);
    }

    public SecretDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
