package io.dev.coding_test.repository;

import io.dev.coding_test.model.Memo;
import io.dev.coding_test.model.enums.SummaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 메모(Memo) 엔티티에 대한 데이터 접근 계층.
 */
public interface MemoRepository extends JpaRepository<Memo, Long> {

    /**
     * 제목 또는 본문에 키워드가 포함된 메모를 대소문자 구분 없이 페이지 단위로 조회한다.
     * <p>
     * {@code Containing} 파생 쿼리는 키워드의 {@code %}, {@code _}를 자동으로 이스케이프한다.
     * </p>
     *
     * @param title    제목 검색 키워드
     * @param content  본문 검색 키워드
     * @param pageable 페이지 정보
     * @return 키워드가 포함된 메모 Page 객체
     */
    Page<Memo> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String title,
                                                                            String content,
                                                                            Pageable pageable);

    /**
     * 요약 상태가 주어진 값 중 하나인 메모를 조회한다.
     * <p>
     * 서버 재시작 전에 끝나지 않은 요약(PENDING/PROCESSING)을 다시 요청할 때 사용한다.
     * </p>
     *
     * @param statuses 조회할 요약 상태 목록
     * @return 해당 상태의 메모 목록
     */
    List<Memo> findBySummaryStatusIn(Collection<SummaryStatus> statuses);

    /**
     * 요약 상태가 주어진 값인 메모를 조회한다. (LLM 설정 저장 후 실패한 요약 재요청)
     *
     * @param status 조회할 요약 상태
     * @return 해당 상태의 메모 목록
     */
    List<Memo> findBySummaryStatus(SummaryStatus status);
}
