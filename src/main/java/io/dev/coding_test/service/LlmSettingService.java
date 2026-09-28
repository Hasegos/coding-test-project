package io.dev.coding_test.service;

import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.LlmModelsRequest;
import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.dto.LlmSettingResponse;
import io.dev.coding_test.llm.client.LlmClientFactory;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.model.LlmSetting;
import io.dev.coding_test.repository.LlmSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 로컬 LLM 서버 접속 설정(LLM 설정 화면)을 처리하는 서비스.
 * <p>
 * 접속 정보는 {@code .env}가 아니라 사용자가 화면에서 입력한 값을 DB에 저장해 사용한다.
 * IP는 요청 DTO의 {@code @LocalIp} 검증으로 루프백 · 사설망 · Tailscale 대역만 허용한다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmSettingService {

    private final LlmSettingRepository llmSettingRepository;
    private final LlmClientFactory llmClientFactory;

    /**
     * 저장된 접속 설정을 조회한다.
     *
     * @return 저장된 설정, 아직 설정하지 않았으면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<LlmSettingResponse> getSetting() {
        return llmSettingRepository.findById(LlmSetting.SINGLETON_ID).map(LlmSettingResponse::from);
    }

    /**
     * LLM 서버 접속 설정이 저장되어 있는지 확인한다.
     *
     * @return 설정되어 있으면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isConfigured() {
        return llmSettingRepository.existsById(LlmSetting.SINGLETON_ID);
    }

    /**
     * 요약에 사용할 접속 정보를 조회한다.
     *
     * @return 접속 정보, 아직 설정하지 않았으면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<LlmConnection> findConnection() {
        return llmSettingRepository.findById(LlmSetting.SINGLETON_ID)
                .map(setting -> new LlmConnection(setting.getProvider(), setting.getHost(),
                        setting.getPort(), setting.getModel(), setting.getApiKey()));
    }

    /**
     * 접속 설정을 저장한다.
     * <p>
     * API Key를 비워두면 기존 토큰을 유지하고, {@code clearApiKey}가 참이면 토큰을 삭제한다.
     * </p>
     *
     * @param request 접속 설정 저장 요청 (검증 완료)
     * @return 저장된 설정
     */
    @Transactional
    public LlmSettingResponse save(LlmSettingRequest request) {
        LlmSetting setting = llmSettingRepository.findById(LlmSetting.SINGLETON_ID).orElseGet(() -> {
            LlmSetting created = new LlmSetting();
            created.setSettingId(LlmSetting.SINGLETON_ID);
            return created;
        });

        setting.setProvider(request.getProvider());
        setting.setHost(request.getHost().strip());
        setting.setPort(request.getPort());
        setting.setModel(request.getModel().strip());
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            setting.setApiKey(null);
        } else if (hasText(request.getApiKey())) {
            setting.setApiKey(request.getApiKey().strip());
        }
        setting.setUpdatedAt(TimeUtil.now());

        llmSettingRepository.save(setting);
        log.info("LLM 설정 저장 - provider: {}, host: {}, port: {}, model: {}",
                setting.getProvider(), setting.getHost(), setting.getPort(), setting.getModel());
        return LlmSettingResponse.from(setting);
    }

    /**
     * 입력한 접속 정보로 LLM 서버에 연결해 사용 가능한 모델 목록을 조회한다. (연결 테스트)
     * API Key를 비워두면 저장된 토큰을 사용한다.
     *
     * @param request 연결 테스트 요청 (검증 완료)
     * @return 모델명 목록
     * @throws io.dev.coding_test.llm.exception.LlmException 연결 실패 또는 응답 형식 오류
     */
    @Transactional(readOnly = true)
    public List<String> listModels(LlmModelsRequest request) {
        String apiKey = hasText(request.apiKey())
                ? request.apiKey().strip()
                : llmSettingRepository.findById(LlmSetting.SINGLETON_ID).map(LlmSetting::getApiKey).orElse(null);
        LlmConnection connection = new LlmConnection(request.provider(), request.host().strip(),
                request.port(), "", apiKey);
        return llmClientFactory.create(connection).listModels();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
