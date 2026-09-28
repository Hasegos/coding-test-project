package io.dev.coding_test.llm.client;

import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;

import java.util.List;

/**
 * 로컬 LLM 서버 하나(접속 정보 + 모델)에 요청하는 클라이언트.
 * <p>
 * 런타임(Ollama, LM Studio)별 구현체는 {@link LlmClientFactory}가 LLM 설정 화면에서 저장한 접속 정보로 만든다.
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
     * LLM 서버에 로드된(사용 가능한) 모델 목록을 조회한다. 연결 테스트에도 사용한다.
     *
     * @return 모델명 목록
     * @throws LlmException LLM 서버에 연결할 수 없거나 응답이 올바르지 않은 경우
     */
    List<String> listModels();

    /**
     * 요약에 사용하는 모델명을 반환한다.
     *
     * @return 모델명
     */
    String model();
}
