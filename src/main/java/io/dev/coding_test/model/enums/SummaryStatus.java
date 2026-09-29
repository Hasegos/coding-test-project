package io.dev.coding_test.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메모 AI 요약 진행 상태.
 */
@Getter
@RequiredArgsConstructor
public enum SummaryStatus {

    /** 요약 대기 중 (저장/수정 직후, 재요약 요청 직후) */
    PENDING("요약 대기"),

    /** 로컬 LLM이 요약 중 */
    PROCESSING("요약 중"),

    /** 요약 완료 */
    DONE("요약 완료"),

    /** 요약 실패 (LLM 연결 실패, 응답 해석 실패 등) */
    FAILED("요약 실패");

    /** 화면에 표시할 상태명 */
    private final String label;
}
