package io.dev.coding_test.service;

import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.setting.LlmConnectionTestRequest;
import io.dev.coding_test.dto.setting.LlmConnectionTestResponse;
import io.dev.coding_test.dto.setting.LlmSettingRequest;
import io.dev.coding_test.dto.setting.LlmSettingResponse;
import io.dev.coding_test.llm.client.LlmClientFactory;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.LlmSetting;
import io.dev.coding_test.repository.LlmSettingRepository;
import io.dev.coding_test.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 로컬 LLM 서버 접속 설정(LLM 설정 화면)을 처리하는 서비스.
 * <p>
 * 접속 정보는 {@code .env}가 아니라 사용자가 화면에서 입력한 값을 DB에 저장해 사용한다.
 * IP는 요청 DTO의 {@code @LocalIp} 검증({@link io.dev.coding_test.llm.guard.LlmHostGuard})으로
 * 사설망 · Tailscale 대역만 허용한다. (localhost·루프백, 링크 로컬 169.254.x, 공인 IP, 도메인은 거부)
 * 설정은 회원마다 하나씩 저장하며, 모든 메서드는 로그인한 회원 자신의 설정만 다룬다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmSettingService {

    private final LlmSettingRepository llmSettingRepository;
    private final UserRepository userRepository;
    private final LlmClientFactory llmClientFactory;

    /**
     * 회원의 접속 설정을 조회한다.
     *
     * @param userId 회원 ID
     * @return 저장된 설정, 아직 설정하지 않았으면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<LlmSettingResponse> getSetting(Long userId) {
        return llmSettingRepository.findById(userId).map(LlmSettingResponse::from);
    }

    /**
     * 회원의 LLM 서버 접속 설정이 저장되어 있는지 확인한다.
     *
     * @param userId 회원 ID
     * @return 설정되어 있으면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isConfigured(Long userId) {
        return llmSettingRepository.existsById(userId);
    }

    /**
     * 요약에 사용할 회원의 접속 정보를 조회한다.
     *
     * @param userId 회원 ID (메모 작성자)
     * @return 접속 정보, 아직 설정하지 않았으면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<LlmConnection> findConnection(Long userId) {
        return llmSettingRepository.findById(userId)
                .map(setting -> new LlmConnection(setting.getProvider(), setting.getHost(),
                        setting.getPort(), setting.getModel(), setting.getApiKey()));
    }

    /**
     * 회원의 접속 설정을 저장한다.
     * <p>
     * API Key를 비워두면 기존 토큰을 유지하고, {@code clearApiKey}가 참이면 토큰을 삭제한다.
     * </p>
     *
     * @param userId  회원 ID
     * @param request 접속 설정 저장 요청 (검증 완료)
     * @return 저장된 설정
     */
    @Transactional
    public LlmSettingResponse save(Long userId, LlmSettingRequest request) {
        LlmSetting setting = llmSettingRepository.findById(userId).orElseGet(() -> {
            LlmSetting created = new LlmSetting();
            created.setUser(userRepository.getReferenceById(userId));
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
        log.info("LLM 설정 저장 - userId: {}, provider: {}, host: {}, port: {}, model: {}", userId,
                setting.getProvider(), setting.getHost(), setting.getPort(), setting.getModel());
        return LlmSettingResponse.from(setting);
    }

    /**
     * 입력한 접속 정보로 LLM 서버에 연결해 사용할 수 있는 모델 목록을 조회한다. (연결 테스트)
     * <p>
     * API Key를 비워두면 회원이 저장한 토큰을 사용한다. 연결 실패도 예외가 아니라 {@code ok = false} 결과로 돌려준다.
     * </p>
     *
     * @param userId  회원 ID
     * @param request 연결 테스트 요청 (검증 완료)
     * @return 연결 테스트 결과
     */
    @Transactional(readOnly = true)
    public LlmConnectionTestResponse testConnection(Long userId, LlmConnectionTestRequest request) {
        String apiKey = hasText(request.apiKey())
                ? request.apiKey().strip()
                : llmSettingRepository.findById(userId).map(LlmSetting::getApiKey).orElse(null);
        LlmConnection connection = new LlmConnection(request.provider(), request.host().strip(),
                request.port(), "", apiKey);

        long start = System.nanoTime();
        try {
            List<String> models = llmClientFactory.create(connection).listModels().orElse(null);
            return LlmConnectionTestResponse.success(elapsedMillis(start), models);
        } catch (LlmException e) {
            return LlmConnectionTestResponse.failure(elapsedMillis(start), e.getMessage());
        }
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
