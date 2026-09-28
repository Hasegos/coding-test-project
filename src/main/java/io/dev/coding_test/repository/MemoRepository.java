package io.dev.coding_test.repository;

import io.dev.coding_test.model.Memo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
