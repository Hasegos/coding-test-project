package io.dev.coding_test.llm.config;

import io.dev.coding_test.llm.guard.LlmGuardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 LLM 호출 공통 설정({@link LlmProperties})과 서버 주소 제한 설정({@link LlmGuardProperties})을 등록한다.
 * <p>
 * LLM 서버 접속 정보는 LLM 설정 화면에서 입력받아 DB에 저장하며,
 * 클라이언트는 {@code LlmClientFactory}가 저장된 접속 정보로 만든다.
 * </p>
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, LlmGuardProperties.class})
public class LlmConfig {
}
