package io.dev.coding_test.event;

import io.dev.coding_test.common.util.SummaryStatusUtil;
import io.dev.coding_test.dto.memo.MemoRevision;
import io.dev.coding_test.llm.queue.LlmServerQueue;
import io.dev.coding_test.repository.MemoRepository;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.service.MemoSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 메모 요약 요청을 메모 작성자의 LLM 서버 대기열({@link LlmServerQueue})로 넘기는 리스너.
 * <p>
 * 같은 LLM 서버를 쓰는 요약은 순서대로, 서로 다른 서버의 요약은 동시에 처리하며 회원 한 명은 동시에 1건만 실행한다.
 * LLM을 설정하지 않은 회원의 요약은 회원별 대기열에 넣는다. (실행하면 설정 안내와 함께 바로 실패 처리)
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoSummaryEventListener {

    private final MemoSummaryService memoSummaryService;
    private final MemoRepository memoRepository;
    private final LlmSettingService llmSettingService;
    private final LlmServerQueue llmServerQueue;

    /**
     * 메모 저장/수정 트랜잭션이 커밋된 뒤 요약 작업을 작성자의 LLM 서버 대기열에 넣는다.
     * <p>
     * 커밋 전에 실행하면 아직 저장되지 않은 메모를 조회할 수 있으므로 {@code AFTER_COMMIT} 단계에서 처리한다.
     * </p>
     *
     * @param event 요약 요청 이벤트
     */
    @TransactionalEventListener
    public void onSummaryRequested(MemoSummaryRequestedEvent event) {
        dispatch(event.memoId(), event.userId(), event.revision());
    }

    /**
     * 서버 기동 시 이전 실행에서 끝나지 않은 요약(PENDING/PROCESSING)을 다시 요청한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedSummaries() {
        List<MemoRevision> unfinished = memoRepository.findRevisionsBySummaryStatusIn(SummaryStatusUtil.IN_PROGRESS);
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("미완료 요약 재요청 - {}건", unfinished.size());
        unfinished.forEach(memo -> dispatch(memo.memoId(), memo.userId(), memo.revision()));
    }

    private void dispatch(Long memoId, Long userId, long revision) {
        String serverKey = llmSettingService.findServerAddress(userId).orElse("user:" + userId);
        try {
            llmServerQueue.submit(serverKey, userId, () -> memoSummaryService.summarize(memoId, revision));
        } catch (TaskRejectedException e) {
            log.warn("요약 대기열 초과 - memoId: {}, 서버: {}", memoId, serverKey);
            memoSummaryService.fail(memoId, revision, "요약 대기열이 가득 찼어요. 잠시 후 다시 요약해주세요.");
        }
    }
}
