package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoListRow;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemberRepository;
import io.dev.coding_test.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 메모 작성·조회·수정·삭제 비즈니스 로직을 처리하는 서비스.
 * <p>
 * 모든 메서드는 로그인한 회원 ID를 받아 그 회원의 메모만 다룬다.
 * 다른 회원의 메모는 없는 메모와 똑같이 {@link NotFoundException}으로 처리한다. (존재 여부 노출 방지)
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoService {

    public static final int MAX_PAGE_SIZE = 50;

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "memoId");

    private final MemoRepository memoRepository;
    private final MemberRepository memberRepository;
    private final MemoSummaryService memoSummaryService;

    /**
     * 메모를 저장한다. 제목/본문의 앞뒤 공백은 제거한다.
     * 저장 트랜잭션이 커밋되면 로컬 LLM 요약이 비동기로 시작된다.
     *
     * @param memberId 작성자 회원 ID
     * @param request  메모 작성 요청
     * @return 저장된 메모
     */
    @Transactional
    public MemoResponse create(Long memberId, MemoRequest request) {
        LocalDateTime now = TimeUtil.now();
        Memo memo = new Memo();
        memo.setMember(memberRepository.getReferenceById(memberId));
        memo.setTitle(request.getTitle().strip());
        memo.setContent(request.getContent().strip());
        memo.setCreatedAt(now);
        memo.setUpdatedAt(now);

        memoRepository.save(memo);
        log.info("메모 저장 - memberId: {}, memoId: {}", memberId, memo.getMemoId());
        memoSummaryService.requestSummary(memo);
        return MemoResponse.from(memo);
    }

    /**
     * 메모 단건을 조회한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param memoId   메모 ID
     * @return 메모
     * @throws NotFoundException 메모가 없거나 다른 회원의 메모일 경우
     */
    @Transactional(readOnly = true)
    public MemoResponse getMemo(Long memberId, Long memoId) {
        Memo memo = memoRepository.findWithTodosByMemoIdAndMemberMemberId(memoId, memberId)
                .orElseThrow(() -> notFound(memoId));
        return MemoResponse.from(memo);
    }

    /**
     * 회원의 메모 목록을 최신순으로 조회한다. 키워드가 있으면 제목/본문에 포함된 메모만 조회한다.
     * <p>
     * 음수 페이지는 0으로, 페이지 크기는 1~{@value #MAX_PAGE_SIZE} 범위로 보정한다.
     * 목록에 필요한 컬럼만 projection으로 조회한다(본문 앞부분, 할 일 개수).
     * </p>
     *
     * @param memberId 로그인한 회원 ID
     * @param keyword  검색 키워드, null 또는 공백이면 전체 조회
     * @param page     페이지 번호 (0부터 시작)
     * @param size     페이지 크기
     * @return 메모 목록 Page 객체
     */
    @Transactional(readOnly = true)
    public Page<MemoListItem> getMemos(Long memberId, String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                LATEST_FIRST
        );

        String trimmed = keyword == null ? "" : keyword.strip();
        Page<MemoListRow> rows = trimmed.isEmpty()
                ? memoRepository.findListRows(memberId, pageable)
                : memoRepository.searchListRows(memberId, likePattern(trimmed), pageable);
        return rows.map(MemoListItem::from);
    }

    /**
     * 검색 키워드를 소문자 LIKE 패턴({@code %키워드%})으로 만든다.
     * 키워드의 역슬래시·%·_는 와일드카드로 해석되지 않도록 역슬래시로 이스케이프한다.
     */
    private static String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 메모 제목과 본문을 수정한다. 제목/본문의 앞뒤 공백은 제거한다.
     * 내용이 실제로 바뀐 경우에만 기존 요약을 비우고 다시 요약한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param memoId   메모 ID
     * @param request  메모 수정 요청
     * @return 수정된 메모
     * @throws NotFoundException 메모가 없거나 다른 회원의 메모일 경우
     */
    @Transactional
    public MemoResponse update(Long memberId, Long memoId, MemoRequest request) {
        Memo memo = findMemo(memberId, memoId);
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
     * @param memberId 로그인한 회원 ID
     * @param memoId   메모 ID
     * @throws NotFoundException 메모가 없거나 다른 회원의 메모일 경우
     */
    @Transactional
    public void delete(Long memberId, Long memoId) {
        memoRepository.delete(findMemo(memberId, memoId));
        log.info("메모 삭제 - memberId: {}, memoId: {}", memberId, memoId);
    }

    /**
     * 회원의 메모 엔티티를 조회한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param memoId   메모 ID
     * @return 메모 엔티티
     * @throws NotFoundException 메모가 없거나 다른 회원의 메모일 경우
     */
    private Memo findMemo(Long memberId, Long memoId) {
        return memoRepository.findByMemoIdAndMemberMemberId(memoId, memberId).orElseThrow(() -> notFound(memoId));
    }

    private static NotFoundException notFound(Long memoId) {
        return new NotFoundException("존재하지 않는 메모입니다. memoId: " + memoId);
    }
}
