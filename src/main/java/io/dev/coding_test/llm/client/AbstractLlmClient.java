package io.dev.coding_test.llm.client;

import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmAuthException;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.exception.LlmUnavailableException;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.prompt.SummaryPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 런타임별 LLM 클라이언트의 공통 흐름을 담당한다.
 * <p>
 * 프롬프트 구성 → 런타임별 HTTP 호출({@link #requestCompletion}, {@link #requestModels}) → 응답 해석 순서로 처리하고,
 * HTTP 호출은 {@link #postJson} / {@link #getJson}으로 보내 다음 규칙을 공통으로 적용한다.
 * </p>
 * <ul>
 *     <li>응답 본문은 {@code llm.max-response-size}까지만 읽는다. (큰 응답으로 메모리를 소모시키는 것 방지)</li>
 *     <li>전체 제한 시간: 요약 {@code llm.read-timeout}, 모델 목록 {@code llm.models-timeout}
 *         (조금씩 보내며 버티는 서버까지 끊는다)</li>
 *     <li>상태 코드를 사용자용 메시지로 바꾸고, LLM 서버의 오류 응답 본문은 노출하지 않는다.
 *         사용자가 입력한 주소의 응답을 그대로 돌려주면 서버가 사설망의 임의 주소 내용을 읽어오는 통로가 되기 때문이다.</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractLlmClient implements LlmClient {

    /** 채팅에 쓸 수 없는 모델(임베딩 / 리랭커 / 음성 / 이미지 등). 모델 목록 API는 이런 모델까지 섞어서 준다. */
    public static final Pattern NON_CHAT_MODEL = Pattern.compile(
            "embed|bge-|rerank|whisper|tts|clip|dall-e|moderation|transcribe",
            Pattern.CASE_INSENSITIVE);

    protected final LlmConnection connection;
    protected final LlmProperties properties;
    private final RestClient chatClient;
    private final RestClient modelsClient;
    private final SummaryResultParser parser;

    /**
     * @param chatClient   요약 요청용 HTTP 클라이언트 (전체 제한 시간 {@code llm.read-timeout})
     * @param modelsClient 모델 목록 조회용 HTTP 클라이언트 (전체 제한 시간 {@code llm.models-timeout})
     */
    protected AbstractLlmClient(RestClient chatClient, RestClient modelsClient, LlmConnection connection,
                                LlmProperties properties, SummaryResultParser parser) {
        this.chatClient = chatClient;
        this.modelsClient = modelsClient;
        this.connection = connection;
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public SummaryResult summarize(String title, String content) {
        long start = System.nanoTime();
        String raw;
        try {
            raw = requestCompletion(SummaryPrompt.SYSTEM, SummaryPrompt.userMessage(title, content));
        } catch (RestClientException e) {
            throw translate(e, properties.readTimeout(), elapsed(start));
        }
        log.info("LLM 응답 수신 - provider: {}, model: {}, {}ms",
                connection.provider(), connection.model(), elapsed(start).toMillis());
        return parser.parse(raw, title + "\n" + content);
    }

    @Override
    public Optional<List<String>> listModels() {
        long start = System.nanoTime();
        try {
            return requestModels().map(models -> models.stream()
                    .filter(model -> model != null && !model.isBlank())
                    .filter(model -> !NON_CHAT_MODEL.matcher(model).find())
                    .distinct()
                    .sorted()
                    .toList());
        } catch (RestClientException e) {
            throw translate(e, properties.modelsTimeout(), elapsed(start));
        }
    }

    @Override
    public String model() {
        return connection.model();
    }

    /**
     * 런타임별 API로 채팅 완성을 요청하고 모델이 생성한 응답 본문을 반환한다.
     *
     * @param system 시스템 프롬프트
     * @param user   사용자 메시지
     * @return 모델이 생성한 응답 문자열
     * @throws LlmException        오류 응답 또는 응답 형식 오류
     * @throws RestClientException HTTP 호출에 실패한 경우
     */
    protected abstract String requestCompletion(String system, String user);

    /**
     * 런타임별 API로 사용 가능한 모델 목록을 조회한다.
     *
     * @return 모델명 목록, 서버가 모델 목록 API를 지원하지 않으면(404) {@code Optional.empty()}
     * @throws LlmException        오류 응답 또는 응답 형식 오류
     * @throws RestClientException HTTP 호출에 실패한 경우
     */
    protected abstract Optional<List<String>> requestModels();

    /**
     * 채팅 API에 JSON을 POST하고 응답 본문을 해석한다. 404는 모델을 찾을 수 없다는 뜻으로 안내한다.
     *
     * @param path 경로 (예: {@code /api/chat})
     * @param body 요청 본문
     * @param type 응답 본문 타입
     * @return 응답 본문
     */
    protected final <T> T postJson(String path, Object body, Class<T> type) {
        return chatClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        throw new LlmException("LLM 서버에서 모델 '" + connection.model()
                                + "'을(를) 찾을 수 없어요. LLM 설정에서 모델을 다시 선택해주세요.");
                    }
                    return readBody(response, status, type);
                });
    }

    /**
     * 모델 목록 API를 GET으로 호출하고 응답 본문을 해석한다.
     *
     * @param path 경로 (예: {@code /api/tags})
     * @param type 응답 본문 타입
     * @return 응답 본문, 404(모델 목록 API 미지원)면 {@code Optional.empty()}
     */
    protected final <T> Optional<T> getJson(String path, Class<T> type) {
        return modelsClient.get()
                .uri(path)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return Optional.empty();
                    }
                    return Optional.of(readBody(response, status, type));
                });
    }

    /**
     * 상태 코드를 확인하고 본문을 최대 크기까지만 읽어 해석한다.
     */
    private <T> T readBody(ClientHttpResponse response, int status, Class<T> type) throws IOException {
        if (status < 200 || status >= 300) {
            log.warn("LLM 서버 오류 응답 - baseUrl: {}, status: {}", connection.baseUrl(), status);
            if (status == 401 || status == 403) {
                throw new LlmAuthException(statusMessage(status));
            }
            throw new LlmException(statusMessage(status));
        }
        byte[] bytes = readLimited(response.getBody(), properties.maxResponseSize().toBytes());
        try {
            T body = JsonMapper.shared().readValue(bytes, type);
            if (body == null) {
                throw new LlmException(connection.provider().getLabel() + " 응답 형식이 올바르지 않아요.");
            }
            return body;
        } catch (JacksonException e) {
            log.warn("LLM 응답 해석 실패 - baseUrl: {}, {}", connection.baseUrl(), e.getOriginalMessage());
            throw new LlmException(connection.provider().getLabel() + " 응답 형식이 올바르지 않아요. "
                    + "IP·포트와 LLM 런타임 선택이 맞는지 확인해주세요.", e);
        }
    }

    private static byte[] readLimited(InputStream body, long maxBytes) throws IOException {
        byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (bytes.length > maxBytes) {
            throw new LlmException("LLM 응답이 너무 커요. (최대 " + maxBytes / 1024 + "KB)");
        }
        return bytes;
    }

    /**
     * 2xx가 아닌 상태 코드를 사용자용 메시지로 바꾼다. (404는 호출한 쪽에서 먼저 처리)
     */
    private static String statusMessage(int status) {
        if (status == 401 || status == 403) {
            return "LLM 서버 인증에 실패했어요. API Key를 확인해주세요.";
        }
        if (status == 400) {
            return "LLM 서버가 요청을 거부했어요. (400) 모델이 로드되어 있는지, 모델명이 맞는지 확인해주세요.";
        }
        if (status == 429) {
            return "LLM 서버 요청 한도를 초과했어요. 잠시 후 다시 시도해주세요.";
        }
        if (status >= 300 && status < 400) {
            return "LLM 서버가 다른 주소로 리다이렉트했어요. IP·포트를 확인해주세요.";
        }
        return "LLM 서버가 " + status + " 응답을 반환했어요.";
    }

    /**
     * HTTP 호출 예외를 사용자용 메시지를 가진 {@link LlmException}으로 변환한다.
     * 연결 실패·시간 초과·통신 실패는 서버가 응답하지 않는 경우이므로 {@link LlmUnavailableException}으로 변환한다.
     * <p>
     * 응답 본문을 읽는 중 전체 제한 시간이 지나면 JDK 요청 팩토리가 스트림을 닫아 {@code IOException: closed}가 되므로,
     * 제한 시간만큼 지난 뒤의 I/O 오류도 시간 초과로 본다.
     * </p>
     */
    private LlmException translate(RestClientException e, Duration timeout, Duration elapsed) {
        String baseUrl = connection.baseUrl();
        if (e instanceof ResourceAccessException) {
            // 연결 타임아웃(HttpConnectTimeoutException)은 HttpTimeoutException의 하위 타입이므로 먼저 확인한다.
            if (hasCause(e, HttpConnectTimeoutException.class) || hasCause(e, ConnectException.class)
                    || hasCause(e, NoRouteToHostException.class)) {
                log.warn("LLM 서버 연결 실패 - baseUrl: {}, {}", baseUrl, e.getMostSpecificCause().toString());
                return new LlmUnavailableException(connectFailureMessage(connection), e);
            }
            if (hasCause(e, HttpTimeoutException.class) || hasCause(e, SocketTimeoutException.class)
                    || elapsed.compareTo(timeout) >= 0) {
                log.warn("LLM 응답 시간 초과 - baseUrl: {}, timeout: {}", baseUrl, timeout);
                return new LlmUnavailableException("LLM 응답 시간(" + format(timeout) + ")이 초과됐어요. "
                        + "서버 상태를 확인하거나 더 작은 모델을 사용해주세요.", e);
            }
        }
        log.warn("LLM 호출 실패 - baseUrl: {}", baseUrl, e);
        return new LlmUnavailableException("LLM 서버와 통신하지 못했어요. 서버 상태와 IP·포트를 확인해주세요.", e);
    }

    /**
     * 연결 실패 안내. Tailscale 주소면 공유·수락 여부처럼 Tailscale 연결에서 흔히 빠뜨리는 항목을 함께 안내한다.
     */
    static String connectFailureMessage(LlmConnection connection) {
        String prefix = "로컬 LLM 서버(" + connection.baseUrl() + ")에 연결할 수 없어요. ";
        if (LlmHostGuard.isTailscaleAddress(connection.host())) {
            return prefix + "LLM PC의 Tailscale과 LLM 서버가 켜져 있는지, "
                    + "LLM PC를 운영자에게 공유했고 운영자가 수락했는지 확인해주세요.";
        }
        return prefix + "서버 실행 여부와 LLM 설정 화면의 IP·포트를 확인해주세요.";
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static String format(Duration duration) {
        return duration.toMillis() % 1000 == 0 ? duration.toSeconds() + "초" : duration.toMillis() + "ms";
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
