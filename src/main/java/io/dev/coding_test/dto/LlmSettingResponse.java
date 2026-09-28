package io.dev.coding_test.dto;

import io.dev.coding_test.model.LlmSetting;
import io.dev.coding_test.model.enums.LlmProvider;

import java.time.LocalDateTime;

/**
 * 저장된 LLM 서버 접속 설정. 인증 토큰 값은 응답에 포함하지 않는다.
 *
 * @param provider  LLM 런타임
 * @param host      LLM 서버 IP
 * @param port      포트
 * @param model     모델명
 * @param hasApiKey 인증 토큰 저장 여부
 * @param updatedAt 마지막 저장 일시
 */
public record LlmSettingResponse(LlmProvider provider,
                                 String host,
                                 int port,
                                 String model,
                                 boolean hasApiKey,
                                 LocalDateTime updatedAt) {

    public static LlmSettingResponse from(LlmSetting setting) {
        return new LlmSettingResponse(
                setting.getProvider(),
                setting.getHost(),
                setting.getPort(),
                setting.getModel(),
                setting.getApiKey() != null && !setting.getApiKey().isBlank(),
                setting.getUpdatedAt()
        );
    }
}
