package io.dev.coding_test.llm.client;

import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.prompt.SummaryPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

/**
 * 런타임별 LLM 클라이언트의 공통 흐름을 담당한다.
 * <p>
 * 프롬프트 구성 → 런타임별 HTTP 호출({@link #requestCompletion}, {@link #requestModels}) → 응답 해석 순서로 처리하고,
 * HTTP 예외는 사용자에게 보여줄 수 있는 {@link LlmException} 메시지로 변환한다.
 * </p>
 */
@Slf4j
public abstract class AbstractLlmClient implements LlmClient {

    private static final int MAX_ERROR_BODY_LENGTH = 200;

    protected final RestClient restClient;
    protected final LlmConnection connection;
    protected final LlmProperties properties;
    private final SummaryResultParser parser;

    protected AbstractLlmClient(RestClient restClient, LlmConnection connection,
                                LlmProperties properties, SummaryResultParser parser) {
        this.restClient = restClient;
        this.connection = connection;
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public SummaryResult summarize(String title, String content) {
        long start = System.currentTimeMillis();
        String raw;
        try {
            raw = requestCompletion(SummaryPrompt.SYSTEM, SummaryPrompt.userMessage(title, content));
        } catch (RestClientException e) {
            throw translate(e);
        }
        log.info("LLM 응답 수신 - provider: {}, model: {}, {}ms",
                connection.provider(), connection.model(), System.currentTimeMillis() - start);
        return parser.parse(raw);
    }

    @Override
    public List<String> listModels() {
        try {
            return requestModels().stream()
                    .filter(model -> model != null && !model.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        } catch (RestClientException e) {
            throw translate(e);
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
     * @throws RestClientException HTTP 호출에 실패한 경우
     */
    protected abstract String requestCompletion(String system, String user);

    /**
     * 런타임별 API로 사용 가능한 모델 목록을 조회한다.
     *
     * @return 모델명 목록
     * @throws RestClientException HTTP 호출에 실패한 경우
     */
    protected abstract List<String> requestModels();

    /**
     * HTTP 호출 예외를 사용자용 메시지를 가진 {@link LlmException}으로 변환한다.
     */
    private LlmException translate(RestClientException e) {
        String baseUrl = connection.baseUrl();
        if (e instanceof RestClientResponseException re) {
            String detail = errorDetail(re);
            log.warn("LLM 서버 오류 응답 - status: {}, detail: {}", re.getStatusCode(), detail);
            return new LlmException("LLM 서버 오류 (HTTP " + re.getStatusCode().value() + ")"
                    + (detail.isEmpty() ? "" : ": " + detail), e);
        }
        if (e instanceof ResourceAccessException) {
            // 연결 타임아웃(HttpConnectTimeoutException)은 HttpTimeoutException의 하위 타입이므로 먼저 확인한다.
            if (hasCause(e, HttpConnectTimeoutException.class) || hasCause(e, ConnectException.class)
                    || hasCause(e, UnknownHostException.class)) {
                log.warn("LLM 서버 연결 실패 - baseUrl: {}, {}", baseUrl, e.getMostSpecificCause().toString());
                return new LlmException("로컬 LLM 서버(" + baseUrl + ")에 연결할 수 없어요. "
                        + "서버 실행 여부와 LLM 설정 화면의 IP·포트를 확인해주세요.", e);
            }
            if (hasCause(e, HttpTimeoutException.class) || hasCause(e, SocketTimeoutException.class)) {
                log.warn("LLM 응답 시간 초과 - baseUrl: {}, timeout: {}", baseUrl, properties.readTimeout());
                return new LlmException("LLM 응답 시간(" + properties.readTimeout().toSeconds()
                        + "초)이 초과됐어요. 더 작은 모델을 쓰거나 llm.read-timeout 설정을 늘려주세요.", e);
            }
        }
        log.warn("LLM 호출 실패 - baseUrl: {}", baseUrl, e);
        return new LlmException("LLM 호출 중 오류가 발생했어요. (" + e.getMostSpecificCause().getMessage() + ")", e);
    }

    /**
     * 오류 응답에서 사용자에게 보여줄 원인 메시지만 꺼낸다.
     * <p>
     * JSON 응답의 {@code error}(문자열 또는 {@code error.message}) / {@code message} 필드만 사용하고,
     * 그 외 응답 본문(HTML 등)은 노출하지 않는다. 연결 테스트로 사설망의 다른 서비스 응답 내용이 새어 나가는 것을 막기 위함이다.
     * </p>
     */
    private static String errorDetail(RestClientResponseException e) {
        MediaType contentType = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getContentType();
        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return "";
        }
        try {
            JsonNode root = JsonMapper.shared().readTree(e.getResponseBodyAsString());
            JsonNode error = root.path("error");
            String detail = error.isString() ? error.asString()
                    : error.path("message").isString() ? error.path("message").asString()
                    : root.path("message").isString() ? root.path("message").asString()
                    : "";
            detail = detail.strip();
            return detail.length() <= MAX_ERROR_BODY_LENGTH ? detail : detail.substring(0, MAX_ERROR_BODY_LENGTH) + "…";
        } catch (JacksonException ignored) {
            return "";
        }
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
