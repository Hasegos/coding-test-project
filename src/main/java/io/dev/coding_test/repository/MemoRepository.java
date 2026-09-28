package io.dev.coding_test.repository;

import io.dev.coding_test.model.Memo;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 메모(Memo) 엔티티에 대한 데이터 접근 계층.
 */
public interface MemoRepository extends JpaRepository<Memo, Long> {
}
