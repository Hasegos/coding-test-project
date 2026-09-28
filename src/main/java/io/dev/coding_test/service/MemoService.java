package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 메모 작성·조회·수정·삭제 비즈니스 로직을 처리하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoService {

    public static final int MAX_PAGE_SIZE = 50;

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "memoId");

    private final MemoRepository memoRepository;
    private final MemoSummaryService memoSummaryService;

    /**
     * 메모를 저장한다. 제목/본문의 앞뒤 공백은 제거한다.
     * 저장 트랜잭션이 커밋되면 로컬 LLM 요약이 비동기로 시작된다.
     *
     * @param request 메모 작성 요청
     * @return 저장된 메모
     */
    @Transactional
    public MemoResponse create(MemoRequest request) {
        LocalDateTime now = TimeUtil.now();
        Memo memo = new Memo();
        memo.setTitle(request.getTitle().strip());
        memo.setContent(request.getContent().strip());
        memo.setCreatedAt(now);
        memo.setUpdatedAt(now);

        memoRepository.save(memo);
        log.info("메모 저장 - memoId: {}", memo.getMemoId());
        memoSummaryService.requestSummary(memo);
        return MemoResponse.from(memo);
    }

    /**
     * 메모 단건을 조회한다.
     *
     * @param memoId 메모 ID
     * @return 메모
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    @Transactional(readOnly = true)
    public MemoResponse getMemo(Long memoId) {
        return MemoResponse.from(findMemo(memoId));
    }

    /**
     * 메모 목록을 최신순으로 조회한다. 키워드가 있으면 제목/본문에 포함된 메모만 조회한다.
     * <p>
     * 음수 페이지는 0으로, 페이지 크기는 1~{@value #MAX_PAGE_SIZE} 범위로 보정한다.
     * </p>
     *
     * @param keyword 검색 키워드, null 또는 공백이면 전체 조회
     * @param page    페이지 번호 (0부터 시작)
     * @param size    페이지 크기
     * @return 메모 목록 Page 객체
     */
    @Transactional(readOnly = true)
    public Page<MemoListItem> getMemos(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                LATEST_FIRST
        );

        String trimmed = keyword == null ? "" : keyword.strip();
        Page<Memo> memos = trimmed.isEmpty()
                ? memoRepository.findAll(pageable)
                : memoRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(trimmed, trimmed, pageable);
        return memos.map(MemoListItem::from);
    }

    /**
     * 메모 제목과 본문을 수정한다. 제목/본문의 앞뒤 공백은 제거한다.
     * 내용이 실제로 바뀐 경우에만 기존 요약을 비우고 다시 요약한다.
     *
     * @param memoId  메모 ID
     * @param request 메모 수정 요청
     * @return 수정된 메모
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    @Transactional
    public MemoResponse update(Long memoId, MemoRequest request) {
        Memo memo = findMemo(memoId);
        String title = request.getTitle().strip();
        String content = request.getContent().strip();
        if (memo.getTitle().equals(title) && memo.getContent().equals(content)) {
            return MemoResponse.from(memo);
        }

        memo.setTitle(title);
        memo.setContent(content);
        memo.setUpdatedAt(TimeUtil.now());
        memo.setRevision(memo.getRevision() + 1);
        log.info("메모 수정 - memoId: {}, revision: {}", memoId, memo.getRevision());

        memoSummaryService.clearSummary(memo);
        memoSummaryService.requestSummary(memo);
        return MemoResponse.from(memo);
    }

    /**
     * 메모를 삭제한다.
     *
     * @param memoId 메모 ID
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    @Transactional
    public void delete(Long memoId) {
        memoRepository.delete(findMemo(memoId));
        log.info("메모 삭제 - memoId: {}", memoId);
    }

    /**
     * 메모 엔티티를 조회한다.
     *
     * @param memoId 메모 ID
     * @return 메모 엔티티
     * @throws NotFoundException 해당 ID의 메모가 없을 경우
     */
    private Memo findMemo(Long memoId) {
        return memoRepository.findById(memoId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 메모입니다. memoId: " + memoId));
    }
}
