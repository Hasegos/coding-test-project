package io.dev.coding_test.dto;

import io.dev.coding_test.common.util.SummaryStatusUtil;
import io.dev.coding_test.model.enums.SummaryStatus;

/**
 * 메모 요약 상태만 담은 응답. (화면의 요약 상태 폴링용 경량 응답)
 *
 * @param status     요약 진행 상태
 * @param inProgress 아직 요약 결과를 기다리는 중(대기/요약 중)인지 여부
 */
public record MemoSummaryStatusResponse(SummaryStatus status, boolean inProgress) {

    public static MemoSummaryStatusResponse of(SummaryStatus status) {
        return new MemoSummaryStatusResponse(status, SummaryStatusUtil.isInProgress(status));
    }
}
