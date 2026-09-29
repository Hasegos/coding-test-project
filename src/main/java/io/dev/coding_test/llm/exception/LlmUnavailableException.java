package io.dev.coding_test.llm.exception;

/**
 * LLM 서버에 연결하지 못했거나 응답 시간이 지나 서버가 응답하지 않는 것으로 볼 때 발생하는 예외.
 * <p>
 * 인증 실패·모델 없음·응답 형식 오류처럼 서버가 응답은 한 경우와 구분해, 연속으로 발생하면 그 서버를 잠시 쉬게 하는 데 쓴다.
 * </p>
 */
public class LlmUnavailableException extends LlmException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
