package io.dev.coding_test.repository;

import io.dev.coding_test.dto.MemoListRow;
import io.dev.coding_test.dto.MemoRevision;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.model.enums.SummaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 메모(Memo) 엔티티에 대한 데이터 접근 계층.
 * <p>
 * 화면·API에서 쓰는 조회·변경은 모두 작성자(회원 ID) 조건을 함께 건다.
 * 다른 회원의 메모는 존재하지 않는 것과 똑같이 취급된다.
 * </p>
 */
public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 목록 projection: 본문은 앞부분만, 할 일은 개수만 조회 */
    String LIST_ROW_SELECT = """
            SELECT new io.dev.coding_test.dto.MemoListRow(
                m.memoId, m.title, SUBSTRING(m.content, 1, """ + MemoListRow.CONTENT_HEAD_LENGTH + """
            ), m.createdAt, m.updatedAt, m.summaryStatus, SIZE(m.todos))
            FROM Memo m
            WHERE m.member.memberId = :memberId
            """;

    /** 제목/본문 키워드 조건 (소문자 LIKE, 역슬래시 이스케이프) */
    String KEYWORD_CONDITION = """
            AND (LOWER(m.title) LIKE :pattern ESCAPE '\\'
                 OR LOWER(m.content) LIKE :pattern ESCAPE '\\')
            """;

    /** 작성자 조건 개수 쿼리 */
    String COUNT_BY_MEMBER = "SELECT COUNT(m) FROM Memo m WHERE m.member.memberId = :memberId ";

    /**
     * 회원의 메모 목록을 페이지 단위로 조회한다. (목록 화면에 필요한 컬럼만 projection)
     * <p>
     * 본문 전체(최대 20,000자) 대신 앞부분만, 할 일은 컬렉션 대신 개수만 조회해
     * 엔티티·컬렉션 로딩 없이 목록 1쿼리 + 개수 1쿼리로 처리한다.
     * </p>
     *
     * @param memberId 작성자 회원 ID
     * @param pageable 페이지 정보 (정렬 포함)
     * @return 메모 목록 Page 객체
     */
    @Query(value = LIST_ROW_SELECT, countQuery = COUNT_BY_MEMBER)
    Page<MemoListRow> findListRows(@Param("memberId") Long memberId, Pageable pageable);

    /**
     * 회원의 메모 중 제목 또는 본문에 키워드가 포함된 메모 목록을 대소문자 구분 없이 조회한다. (목록 projection)
     *
     * @param memberId 작성자 회원 ID
     * @param pattern  소문자 LIKE 패턴 ({@code %키워드%}, 키워드의 {@code \ % _}는 {@code \}로 이스케이프)
     * @param pageable 페이지 정보 (정렬 포함)
     * @return 키워드가 포함된 메모 목록 Page 객체
     */
    @Query(value = LIST_ROW_SELECT + KEYWORD_CONDITION, countQuery = COUNT_BY_MEMBER + KEYWORD_CONDITION)
    Page<MemoListRow> searchListRows(@Param("memberId") Long memberId, @Param("pattern") String pattern,
                                     Pageable pageable);

    /**
     * 회원의 메모를 할 일 목록과 함께 한 번에 조회한다. (상세 화면, 요약 결과 조회)
     *
     * @param memoId   메모 ID
     * @param memberId 작성자 회원 ID
     * @return 할 일이 로딩된 메모, 없거나 다른 회원의 메모면 {@code Optional.empty()}
     */
    @EntityGraph(attributePaths = "todos")
    Optional<Memo> findWithTodosByMemoIdAndMemberMemberId(Long memoId, Long memberId);

    /**
     * 회원의 메모를 조회한다. (수정·삭제·재요약)
     *
     * @param memoId   메모 ID
     * @param memberId 작성자 회원 ID
     * @return 메모, 없거나 다른 회원의 메모면 {@code Optional.empty()}
     */
    Optional<Memo> findByMemoIdAndMemberMemberId(Long memoId, Long memberId);

    /**
     * 회원 메모의 요약 상태만 조회한다. (화면의 요약 상태 폴링)
     *
     * @param memoId   메모 ID
     * @param memberId 작성자 회원 ID
     * @return 요약 상태, 없거나 다른 회원의 메모면 {@code Optional.empty()}
     */
    @Query("SELECT m.summaryStatus FROM Memo m WHERE m.memoId = :memoId AND m.member.memberId = :memberId")
    Optional<SummaryStatus> findSummaryStatus(@Param("memoId") Long memoId, @Param("memberId") Long memberId);


    /**
     * 요약 상태가 주어진 값 중 하나인 메모의 ID와 revision만 조회한다. (전체 회원)
     * <p>
     * 서버 재시작 전에 끝나지 않은 요약(PENDING/PROCESSING)을 다시 요청할 때 사용한다.
     * </p>
     *
     * @param statuses 조회할 요약 상태 목록
     * @return 메모 ID·revision 목록
     */
    @Query("SELECT new io.dev.coding_test.dto.MemoRevision(m.memoId, m.revision) FROM Memo m WHERE m.summaryStatus IN :statuses")
    List<MemoRevision> findRevisionsBySummaryStatusIn(@Param("statuses") Collection<SummaryStatus> statuses);

    /**
     * 회원의 메모 중 요약 상태가 주어진 값인 메모의 ID와 revision만 조회한다. (실패한 요약 재요청)
     *
     * @param memberId 작성자 회원 ID
     * @param status   요약 상태
     * @return 메모 ID·revision 목록
     */
    @Query("""
            SELECT new io.dev.coding_test.dto.MemoRevision(m.memoId, m.revision) FROM Memo m
            WHERE m.member.memberId = :memberId AND m.summaryStatus = :status
            """)
    List<MemoRevision> findRevisions(@Param("memberId") Long memberId, @Param("status") SummaryStatus status);

    /**
     * 회원의 메모 중 주어진 상태의 메모를 모두 요약 대기(PENDING) 상태로 바꾸고 실패 사유를 비운다. (일괄 UPDATE)
     *
     * @param memberId 작성자 회원 ID
     * @param from     변경 전 요약 상태
     * @return 변경된 메모 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Memo m SET m.summaryStatus = io.dev.coding_test.model.enums.SummaryStatus.PENDING, m.summaryError = NULL
            WHERE m.member.memberId = :memberId AND m.summaryStatus = :from
            """)
    int markPendingByStatus(@Param("memberId") Long memberId, @Param("from") SummaryStatus from);
}
