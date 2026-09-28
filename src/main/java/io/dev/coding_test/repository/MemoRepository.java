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
 */
public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 목록 projection: 본문은 앞부분만, 할 일은 개수만 조회 */
    String LIST_ROW_SELECT = """
            SELECT new io.dev.coding_test.dto.MemoListRow(
                m.memoId, m.title, SUBSTRING(m.content, 1, """ + MemoListRow.CONTENT_HEAD_LENGTH + """
            ), m.createdAt, m.updatedAt, m.summaryStatus, SIZE(m.todos))
            FROM Memo m
            """;

    /** 제목/본문 키워드 조건 (소문자 LIKE, 역슬래시 이스케이프) */
    String KEYWORD_CONDITION = """
            WHERE LOWER(m.title) LIKE :pattern ESCAPE '\\'
               OR LOWER(m.content) LIKE :pattern ESCAPE '\\'
            """;

    /**
     * 메모 목록을 페이지 단위로 조회한다. (목록 화면에 필요한 컬럼만 projection)
     * <p>
     * 본문 전체(최대 20,000자) 대신 앞부분만, 할 일은 컬렉션 대신 개수만 조회해
     * 엔티티·컬렉션 로딩 없이 목록 1쿼리 + 개수 1쿼리로 처리한다.
     * </p>
     *
     * @param pageable 페이지 정보 (정렬 포함)
     * @return 메모 목록 Page 객체
     */
    @Query(value = LIST_ROW_SELECT, countQuery = "SELECT COUNT(m) FROM Memo m")
    Page<MemoListRow> findListRows(Pageable pageable);

    /**
     * 제목 또는 본문에 키워드가 포함된 메모 목록을 대소문자 구분 없이 조회한다. (목록 projection)
     *
     * @param pattern  소문자 LIKE 패턴 ({@code %키워드%}, 키워드의 {@code \ % _}는 {@code \}로 이스케이프)
     * @param pageable 페이지 정보 (정렬 포함)
     * @return 키워드가 포함된 메모 목록 Page 객체
     */
    @Query(value = LIST_ROW_SELECT + KEYWORD_CONDITION,
            countQuery = "SELECT COUNT(m) FROM Memo m " + KEYWORD_CONDITION)
    Page<MemoListRow> searchListRows(@Param("pattern") String pattern, Pageable pageable);

    /**
     * 메모를 할 일 목록과 함께 한 번에 조회한다. (상세 화면, 요약 결과 조회)
     *
     * @param memoId 메모 ID
     * @return 할 일이 로딩된 메모, 없으면 {@code Optional.empty()}
     */
    @EntityGraph(attributePaths = "todos")
    Optional<Memo> findWithTodosByMemoId(Long memoId);

    /**
     * 메모의 요약 상태만 조회한다. (화면의 요약 상태 폴링)
     *
     * @param memoId 메모 ID
     * @return 요약 상태, 메모가 없으면 {@code Optional.empty()}
     */
    @Query("SELECT m.summaryStatus FROM Memo m WHERE m.memoId = :memoId")
    Optional<SummaryStatus> findSummaryStatus(@Param("memoId") Long memoId);

    /**
     * 요약 상태가 주어진 값 중 하나인 메모의 ID와 revision만 조회한다.
     * <p>
     * 서버 재시작 전에 끝나지 않은 요약(PENDING/PROCESSING)이나 실패한 요약을 다시 요청할 때 사용한다.
     * </p>
     *
     * @param statuses 조회할 요약 상태 목록
     * @return 메모 ID·revision 목록
     */
    @Query("SELECT new io.dev.coding_test.dto.MemoRevision(m.memoId, m.revision) FROM Memo m WHERE m.summaryStatus IN :statuses")
    List<MemoRevision> findRevisionsBySummaryStatusIn(@Param("statuses") Collection<SummaryStatus> statuses);

    /**
     * 주어진 상태의 메모를 모두 요약 대기(PENDING) 상태로 바꾸고 실패 사유를 비운다. (일괄 UPDATE)
     *
     * @param from 변경 전 요약 상태
     * @return 변경된 메모 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Memo m SET m.summaryStatus = io.dev.coding_test.model.enums.SummaryStatus.PENDING, m.summaryError = NULL WHERE m.summaryStatus = :from")
    int markPendingByStatus(@Param("from") SummaryStatus from);
}
