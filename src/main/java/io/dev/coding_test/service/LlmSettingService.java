package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.InvalidFieldException;
import io.dev.coding_test.common.exception.TooManyRequestsException;
import io.dev.coding_test.common.util.IpAddressUtil;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.setting.LlmConnectionTestRequest;
import io.dev.coding_test.dto.setting.LlmConnectionTestResponse;
import io.dev.coding_test.dto.setting.LlmSettingRequest;
import io.dev.coding_test.dto.setting.LlmSettingResponse;
import io.dev.coding_test.llm.client.LlmClientFactory;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.exception.LlmAuthException;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.guard.LlmProbeLimiter;
import io.dev.coding_test.llm.queue.LlmServerBreaker;
import io.dev.coding_test.model.LlmSetting;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.repository.LlmSettingRepository;
import io.dev.coding_test.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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

    public static final String SHARED_KEY_REQUIRED_MESSAGE =
            "다른 회원이 이미 등록한 LLM 서버예요. 본인 서버라면 LLM 서버의 인증 토큰을 API Key에 입력해주세요.";
    public static final String SHARED_NO_AUTH_MESSAGE =
            "다른 회원이 이미 등록한 LLM 서버인데 인증이 꺼져 있어요. LLM 서버에서 인증 토큰을 켠 뒤 그 토큰을 입력해주세요. "
                    + "(Ollama는 자체 인증이 없어 여러 회원이 함께 쓸 수 없어요)";
    public static final String SHARED_WRONG_KEY_MESSAGE =
            "API Key가 올바르지 않아요. 다른 회원이 이미 등록한 LLM 서버는 그 서버의 인증 토큰으로만 등록할 수 있어요.";
    public static final String SHARED_UNREACHABLE_MESSAGE =
            "다른 회원이 이미 등록한 LLM 서버라 인증 토큰을 확인해야 하는데, 서버에 연결하지 못했어요. LLM 서버를 켠 뒤 다시 저장해주세요.";

    private final LlmSettingRepository llmSettingRepository;
    private final UserRepository userRepository;
    private final LlmClientFactory llmClientFactory;
    private final LlmProbeLimiter llmProbeLimiter;
    private final LlmServerBreaker llmServerBreaker;

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
     * 회원의 LLM 서버 주소({@code host:port})를 조회한다. (요약 대기열 선택용)
     *
     * @param userId 회원 ID (메모 작성자)
     * @return {@code host:port}, 아직 설정하지 않았으면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<String> findServerAddress(Long userId) {
        return llmSettingRepository.findServerAddress(userId);
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
     * <p>
     * 다른 회원이 이미 등록한 LLM 서버(IP·포트)로 바꾸려면 그 서버의 인증 토큰을 알아야 한다. ({@link #verifySharedServer})
     * 서버 IP만 알면 남의 LLM(GPU)을 쓸 수 있는 것을 막는다.
     * </p>
     * <p>
     * 저장하면 그 서버가 연속 실패로 쉬는 중이어도 다음 요약 1건은 바로 시험한다. ({@link LlmServerBreaker#allowTrial})
     * </p>
     *
     * @param userId  회원 ID
     * @param request 접속 설정 저장 요청 (검증 완료)
     * @return 저장된 설정
     * @throws TooManyRequestsException 런타임·주소·포트를 짧은 시간에 너무 자주 바꾼 경우
     * @throws InvalidFieldException    다른 회원이 등록한 LLM 서버인데 인증 토큰을 확인하지 못한 경우
     */
    @Transactional
    public LlmSettingResponse save(Long userId, LlmSettingRequest request) {
        LlmSetting setting = llmSettingRepository.findById(userId).orElseGet(() -> {
            LlmSetting created = new LlmSetting();
            created.setUser(userRepository.getReferenceById(userId));
            return created;
        });
        if (isAddressChanged(setting, request)) {
            llmProbeLimiter.tryAcquire(userId).ifPresent(retryAfter -> {
                throw new TooManyRequestsException(LlmProbeLimiter.message(retryAfter));
            });
            if (isRegisteredByOthers(userId, request.getHost(), request.getPort())) {
                String apiKey = Boolean.TRUE.equals(request.getClearApiKey()) ? null
                        : hasText(request.getApiKey()) ? request.getApiKey().strip() : setting.getApiKey();
                verifySharedServer(request.getProvider(), request.getHost().strip(), request.getPort(), apiKey);
            }
        }

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
        llmServerBreaker.allowTrial(setting.getHost() + ":" + setting.getPort());
        log.info("LLM 설정 저장 - userId: {}, provider: {}, host: {}, port: {}, model: {}", userId,
                setting.getProvider(), setting.getHost(), setting.getPort(), setting.getModel());
        return LlmSettingResponse.from(setting);
    }

    /**
     * 입력한 접속 정보로 LLM 서버에 연결해 사용할 수 있는 모델 목록을 조회한다. (연결 테스트)
     * <p>
     * API Key를 비워두면 회원이 저장한 토큰을 사용한다. 연결 실패도 예외가 아니라 {@code ok = false} 결과로 돌려준다.
     * 짧은 시간에 너무 많이 시도하면({@link LlmProbeLimiter}) 연결하지 않고 안내 메시지를 돌려준다.
     * </p>
     *
     * @param userId  회원 ID
     * @param request 연결 테스트 요청 (검증 완료)
     * @return 연결 테스트 결과
     */
    @Transactional(readOnly = true)
    public LlmConnectionTestResponse testConnection(Long userId, LlmConnectionTestRequest request) {
        Optional<Duration> limited = llmProbeLimiter.tryAcquire(userId);
        if (limited.isPresent()) {
            return LlmConnectionTestResponse.failure(0, LlmProbeLimiter.message(limited.get()));
        }
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

    /**
     * 다른 회원이 같은 LLM 서버(IP·포트)를 이미 등록했는지 확인한다. IPv6는 표기가 달라도 같은 주소로 본다.
     */
    private boolean isRegisteredByOthers(Long userId, String host, int port) {
        Optional<InetAddress> address = IpAddressUtil.parseLiteral(host.strip());
        if (address.isEmpty()) {
            return false;
        }
        return llmSettingRepository.findByPortAndUserIdNot(port, userId).stream()
                .anyMatch(other -> IpAddressUtil.parseLiteral(other.getHost()).equals(address));
    }

    /**
     * 다른 회원이 이미 등록한 LLM 서버를 등록할 때, 그 서버의 인증 토큰을 아는지 확인한다.
     * <ol>
     *     <li>토큰 없이 요청하면 서버가 인증을 요구해야 한다. (인증이 꺼진 서버는 아무 토큰이나 통과하므로 확인할 수 없다)</li>
     *     <li>입력한 토큰으로 요청하면 성공해야 한다.</li>
     * </ol>
     * 드문 경우라 저장 트랜잭션 안에서 모델 목록 조회(최대 {@code llm.models-timeout})를 두 번 한다.
     */
    private void verifySharedServer(LlmProvider provider, String host, int port, String apiKey) {
        if (!hasText(apiKey)) {
            throw new InvalidFieldException("apiKey", SHARED_KEY_REQUIRED_MESSAGE);
        }
        try {
            llmClientFactory.create(new LlmConnection(provider, host, port, "", null)).listModels();
            throw new InvalidFieldException("apiKey", SHARED_NO_AUTH_MESSAGE);
        } catch (LlmAuthException expected) {
            // 인증을 요구하는 서버 — 입력한 토큰을 확인한다.
        } catch (LlmException e) {
            throw new InvalidFieldException("host", SHARED_UNREACHABLE_MESSAGE);
        }
        try {
            llmClientFactory.create(new LlmConnection(provider, host, port, "", apiKey)).listModels();
        } catch (LlmAuthException e) {
            throw new InvalidFieldException("apiKey", SHARED_WRONG_KEY_MESSAGE);
        } catch (LlmException e) {
            throw new InvalidFieldException("host", SHARED_UNREACHABLE_MESSAGE);
        }
        log.info("다른 회원이 등록한 LLM 서버 등록 - 인증 토큰 확인 완료, host: {}, port: {}", host, port);
    }

    /**
     * 저장된 설정과 런타임·주소·포트가 다른지 확인한다. (처음 저장하는 경우 포함)
     */
    private static boolean isAddressChanged(LlmSetting setting, LlmSettingRequest request) {
        return setting.getHost() == null
                || setting.getProvider() != request.getProvider()
                || !setting.getHost().equals(request.getHost().strip())
                || setting.getPort() != request.getPort();
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
