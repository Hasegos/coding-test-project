package io.dev.coding_test.common.exception;

/**
 * 이미 사용 중인 아이디로 가입하려 할 때 발생하는 예외.
 * <p>
 * 중복 검사 후 저장 사이에 같은 아이디가 먼저 가입된 경우(아이디 unique 제약 위반)에 사용한다.
 * </p>
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String message) {
        super(message);
    }
}
