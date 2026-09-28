package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.dto.MemoSummaryResponse;
import io.dev.coding_test.event.MemoSummaryRequestedEvent;
import io.dev.coding_test.llm.LlmClient;
import io.dev.coding_test.llm.LlmException;
import io.dev.coding_test.llm.SummaryResult;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * 로컬 LLM을 이용한 메모 요약·할 일 추출 로직을 처리하는 서비스.
 * <p>
 * 처리 흐름:
 * </p>
 * <ol>
 *     <li>메모 저장/수정/재요약 요청 시 {@link #requestSummary(Memo)}가 PENDING으로 바꾸고 이벤트를 발행한다.</li>
 *     <li>트랜잭션 커밋 후 {@code MemoSummaryEventListener}가 LLM 전용 실행기에서 {@link #summarize(Long, long)}를 실행한다.</li>
 *     <li>LLM 호출은 DB 커넥션을 잡지 않도록 트랜잭션 밖에서 수행하고, 결과 반영은 짧은 새 트랜잭션에서 처리한다.</li>
 *     <li>요약 중 메모가 수정·삭제되면 revision이 달라지므로 오래된 결과는 버린다.</li>
 * </ol>
 */
@Slf4j
@Service
public class MemoSummaryService {

    public static final int MAX_ERROR_LENGTH = 500;

    private final MemoRepository memoRepository;
    private final LlmClient llmClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate newTransaction;

    public MemoSummaryService(MemoRepository memoRepository,
                              LlmClient llmClient,
                              ApplicationEventPublisher eventPublisher,
                              PlatformTransactionManager transactionManager) {
        this.memoRepository = memoRepository;
        this.llmClient = llmClient;
        this.eventPublisher = eventPublisher;
        // 커밋 후(AFTER_COMMIT) 콜백에서도 항상 독립된 트랜잭션으로 반영되도록 REQUIRES_NEW 사용
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 메모를 요약 대기 상태로 바꾸고 요약 요청 이벤트를 발행한다.
     * 호출한 트랜잭션이 커밋된 뒤에 실제 요약이 시작된다.
     *
     * @param memo 요약할 메모 (영속 상태)
     */
    public void requestSummary(Memo memo) {
        memo.markSummaryPending();
        eventPublisher.publishEvent(new MemoSummaryRequestedEvent(memo.getMemoId(), memo.getRevision()));
        log.info("요약 요청 - memoId: {}, revision: {}", memo.getMemoId(), memo.getRevision());
    }

    /**
     * 메모의 요약 결과를 조회한다.
     *
     * @param memoId 메모 ID
     * @return 요약 결과
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    @Transactional(readOnly = true)
    public MemoSummaryResponse getSummary(Long memoId) {
        return MemoSummaryResponse.from(findMemo(memoId));
    }

    /**
     * 메모 재요약을 요청한다. 이미 요약 중(PENDING/PROCESSING)이면 중복 요청하지 않는다.
     *
     * @param memoId 메모 ID
     * @return 요청 후 요약 상태
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    @Transactional
    public MemoSummaryResponse retry(Long memoId) {
        Memo memo = findMemo(memoId);
        if (!memo.getSummaryStatus().isInProgress()) {
            requestSummary(memo);
        }
        return MemoSummaryResponse.from(memo);
    }

    /**
     * 로컬 LLM으로 메모를 요약하고 결과를 반영한다. (LLM 전용 실행기에서 호출)
     *
     * @param memoId   메모 ID
     * @param revision 요약 요청 시점의 메모 revision
     */
    public void summarize(Long memoId, long revision) {
        Optional<MemoSnapshot> snapshot = newTransaction.execute(status ->
                findCurrent(memoId, revision).map(memo -> {
                    memo.markSummaryProcessing();
                    return new MemoSnapshot(memo.getTitle(), memo.getContent());
                }));
        if (snapshot == null || snapshot.isEmpty()) {
            log.info("요약 건너뜀(삭제 또는 수정됨) - memoId: {}, revision: {}", memoId, revision);
            return;
        }

        SummaryResult result;
        try {
            result = llmClient.summarize(snapshot.get().title(), snapshot.get().content());
        } catch (LlmException e) {
            log.warn("요약 실패 - memoId: {}, 사유: {}", memoId, e.getMessage());
            fail(memoId, revision, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("요약 중 예상치 못한 오류 - memoId: {}", memoId, e);
            fail(memoId, revision, "요약 중 알 수 없는 오류가 발생했어요.");
            return;
        }

        newTransaction.executeWithoutResult(status ->
                findCurrent(memoId, revision).ifPresentOrElse(
                        memo -> {
                            memo.completeSummary(result.summary(), result.todos(), llmClient.model());
                            log.info("요약 완료 - memoId: {}, 할 일: {}개", memoId, result.todos().size());
                        },
                        () -> log.info("요약 결과 폐기(삭제 또는 수정됨) - memoId: {}, revision: {}", memoId, revision)
                ));
    }

    /**
     * 요약 실패를 기록한다. 요청 이후 메모가 수정·삭제됐다면 무시한다.
     *
     * @param memoId   메모 ID
     * @param revision 요약 요청 시점의 메모 revision
     * @param error    사용자에게 보여줄 실패 사유
     */
    public void fail(Long memoId, long revision, String error) {
        String message = error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH - 1) + "…";
        newTransaction.executeWithoutResult(status ->
                findCurrent(memoId, revision).ifPresent(memo -> memo.failSummary(message)));
    }

    /**
     * 요청 시점과 revision이 같은(=그 사이 수정되지 않은) 메모만 조회한다.
     */
    private Optional<Memo> findCurrent(Long memoId, long revision) {
        return memoRepository.findById(memoId).filter(memo -> memo.getRevision() == revision);
    }

    private Memo findMemo(Long memoId) {
        return memoRepository.findById(memoId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 메모입니다. memoId: " + memoId));
    }

    private record MemoSnapshot(String title, String content) {
    }
}
