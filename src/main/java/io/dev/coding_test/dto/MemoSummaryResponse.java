package io.dev.coding_test.dto;

import io.dev.coding_test.model.Memo;
import io.dev.coding_test.model.MemoTodo;
import io.dev.coding_test.model.SummaryStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메모 AI 요약 결과.
 *
 * @param status       요약 진행 상태
 * @param summary      요약문, 요약 전이면 null
 * @param todos        추출된 할 일 목록
 * @param error        요약 실패 사유, 실패가 아니면 null
 * @param model        요약에 사용한 LLM 모델명
 * @param summarizedAt 요약 완료 일시
 */
public record MemoSummaryResponse(SummaryStatus status,
                                  String summary,
                                  List<String> todos,
                                  String error,
                                  String model,
                                  LocalDateTime summarizedAt) {

    public static MemoSummaryResponse from(Memo memo) {
        return new MemoSummaryResponse(
                memo.getSummaryStatus(),
                memo.getSummary(),
                memo.getTodos().stream().map(MemoTodo::getContent).toList(),
                memo.getSummaryError(),
                memo.getSummaryModel(),
                memo.getSummarizedAt()
        );
    }
}
