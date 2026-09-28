package io.dev.coding_test.llm.client;

import io.dev.coding_test.llm.config.LlmConfig;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;


/**
 * 로컬 LLM에 메모 요약을 요청하는 클라이언트.
 * <p>
 * 런타임(Ollama, LM Studio)별 구현체는 {@link LlmConfig}가 {@code llm.provider} 설정에 따라 하나만 등록한다.
 * </p>
 */
public interface LlmClient {

    /**
     * 메모를 요약하고 할 일 목록을 추출한다.
     *
     * @param title   메모 제목
     * @param content 메모 본문
     * @return 요약 결과
     * @throws LlmException LLM 호출 또는 응답 해석에 실패한 경우
     */
    SummaryResult summarize(String title, String content);

    /**
     * 요약에 사용하는 모델명을 반환한다.
     *
     * @return 모델명
     */
    String model();
}
