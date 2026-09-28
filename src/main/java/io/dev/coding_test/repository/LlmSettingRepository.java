package io.dev.coding_test.repository;

import io.dev.coding_test.model.LlmSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * LLM 서버 접속 설정(LlmSetting) 엔티티에 대한 데이터 접근 계층.
 */
public interface LlmSettingRepository extends JpaRepository<LlmSetting, Long> {
}
