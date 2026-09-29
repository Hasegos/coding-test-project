package io.dev.coding_test.common.exception;

/**
 * 짧은 시간에 같은 요청을 너무 많이 보냈을 때 발생하는 예외. (API는 429로 응답)
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
