package io.dev.coding_test.repository;

import io.dev.coding_test.model.LlmSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * LLM 서버 접속 설정(LlmSetting) 엔티티에 대한 데이터 접근 계층.
 */
public interface LlmSettingRepository extends JpaRepository<LlmSetting, Long> {

    /**
     * 회원의 LLM 서버 주소({@code host:port})만 조회한다. (API Key 복호화 없이 요약 대기열을 고를 때 사용)
     *
     * @param userId 회원 ID
     * @return {@code host:port}, 설정하지 않았으면 {@code Optional.empty()}
     */
    @Query("SELECT CONCAT(s.host, ':', CAST(s.port AS String)) FROM LlmSetting s WHERE s.userId = :userId")
    Optional<String> findServerAddress(@Param("userId") Long userId);
}
