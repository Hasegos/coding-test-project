package io.dev.coding_test.event;

import io.dev.coding_test.common.config.AsyncConfig;
import io.dev.coding_test.common.util.SummaryStatusUtil;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import io.dev.coding_test.service.MemoSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 메모 요약 요청을 LLM 전용 실행기로 넘기는 리스너.
 */
@Slf4j
@Component
public class MemoSummaryEventListener {

    private final MemoSummaryService memoSummaryService;
    private final MemoRepository memoRepository;
    private final TaskExecutor llmExecutor;

    public MemoSummaryEventListener(MemoSummaryService memoSummaryService,
                                    MemoRepository memoRepository,
                                    @Qualifier(AsyncConfig.LLM_EXECUTOR) TaskExecutor llmExecutor) {
        this.memoSummaryService = memoSummaryService;
        this.memoRepository = memoRepository;
        this.llmExecutor = llmExecutor;
    }

    /**
     * 메모 저장/수정 트랜잭션이 커밋된 뒤 요약 작업을 실행기에 등록한다.
     * <p>
     * 커밋 전에 실행하면 아직 저장되지 않은 메모를 조회할 수 있으므로 {@code AFTER_COMMIT} 단계에서 처리한다.
     * </p>
     *
     * @param event 요약 요청 이벤트
     */
    @TransactionalEventListener
    public void onSummaryRequested(MemoSummaryRequestedEvent event) {
        dispatch(event.memoId(), event.revision());
    }

    /**
     * 서버 기동 시 이전 실행에서 끝나지 않은 요약(PENDING/PROCESSING)을 다시 요청한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedSummaries() {
        List<Memo> unfinished = memoRepository.findBySummaryStatusIn(SummaryStatusUtil.IN_PROGRESS);
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("미완료 요약 재요청 - {}건", unfinished.size());
        unfinished.forEach(memo -> dispatch(memo.getMemoId(), memo.getRevision()));
    }

    private void dispatch(Long memoId, long revision) {
        try {
            llmExecutor.execute(() -> memoSummaryService.summarize(memoId, revision));
        } catch (TaskRejectedException e) {
            log.warn("요약 대기열 초과 - memoId: {}", memoId);
            memoSummaryService.fail(memoId, revision, "요약 대기열이 가득 찼어요. 잠시 후 다시 요약해주세요.");
        }
    }
}
