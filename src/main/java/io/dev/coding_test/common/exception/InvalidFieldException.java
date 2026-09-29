package io.dev.coding_test.common.exception;

import lombok.Getter;

/**
 * 형식 검증은 통과했지만 서비스 규칙에 맞지 않는 입력값이 있을 때 발생하는 예외.
 * <p>
 * API는 400 + 필드 오류로 응답하고, 화면은 해당 입력칸 아래에 메시지를 보여준다.
 * </p>
 */
@Getter
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
}
