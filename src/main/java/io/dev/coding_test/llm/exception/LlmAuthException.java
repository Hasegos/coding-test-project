package io.dev.coding_test.llm.exception;

/**
 * LLM 서버가 인증 실패(401 / 403)로 응답했을 때 발생하는 예외.
 * <p>
 * 다른 회원이 이미 등록한 LLM 서버를 등록할 때, 서버가 인증을 요구하는지·입력한 토큰이 맞는지 구분하는 데 쓴다.
 * </p>
 */
public class LlmAuthException extends LlmException {

    public LlmAuthException(String message) {
        super(message);
    }
}
