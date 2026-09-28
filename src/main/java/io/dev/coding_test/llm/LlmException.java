package io.dev.coding_test.llm;

/**
 * 로컬 LLM 호출 또는 응답 해석에 실패했을 때 발생하는 예외.
 * <p>
 * 메시지는 요약 실패 사유로 화면에 그대로 노출되므로 사용자가 이해할 수 있는 문장으로 작성한다.
 * </p>
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
