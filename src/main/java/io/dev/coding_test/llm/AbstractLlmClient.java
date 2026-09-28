package io.dev.coding_test.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * 런타임별 LLM 클라이언트의 공통 흐름을 담당한다.
 * <p>
 * 프롬프트 구성 → 런타임별 HTTP 호출({@link #requestCompletion}) → 응답 해석 순서로 처리하고,
 * HTTP 예외는 사용자에게 보여줄 수 있는 {@link LlmException} 메시지로 변환한다.
 * </p>
 */
@Slf4j
public abstract class AbstractLlmClient implements LlmClient {

    private static final int MAX_ERROR_BODY_LENGTH = 200;

    protected final RestClient restClient;
    protected final LlmProperties properties;
    private final SummaryResultParser parser;

    protected AbstractLlmClient(RestClient restClient, LlmProperties properties, SummaryResultParser parser) {
        this.restClient = restClient;
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
                properties.provider(), properties.model(), System.currentTimeMillis() - start);
        return parser.parse(raw);
    }

    @Override
    public String model() {
        return properties.model();
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
     * HTTP 호출 예외를 사용자용 메시지를 가진 {@link LlmException}으로 변환한다.
     */
    private LlmException translate(RestClientException e) {
        String baseUrl = properties.resolvedBaseUrl();
        if (e instanceof RestClientResponseException re) {
            String body = re.getResponseBodyAsString().strip();
            if (body.length() > MAX_ERROR_BODY_LENGTH) {
                body = body.substring(0, MAX_ERROR_BODY_LENGTH) + "…";
            }
            log.warn("LLM 서버 오류 응답 - status: {}, body: {}", re.getStatusCode(), body);
            return new LlmException("LLM 서버 오류 (HTTP " + re.getStatusCode().value() + ")"
                    + (body.isEmpty() ? "" : ": " + body), e);
        }
        if (e instanceof ResourceAccessException) {
            // 연결 타임아웃(HttpConnectTimeoutException)은 HttpTimeoutException의 하위 타입이므로 먼저 확인한다.
            if (hasCause(e, HttpConnectTimeoutException.class) || hasCause(e, ConnectException.class)
                    || hasCause(e, UnknownHostException.class)) {
                log.warn("LLM 서버 연결 실패 - baseUrl: {}, {}", baseUrl, e.getMostSpecificCause().toString());
                return new LlmException("로컬 LLM 서버(" + baseUrl + ")에 연결할 수 없어요. "
                        + "서버 실행 여부와 llm.base-url 설정을 확인해주세요.", e);
            }
            if (hasCause(e, HttpTimeoutException.class) || hasCause(e, SocketTimeoutException.class)) {
                log.warn("LLM 응답 시간 초과 - baseUrl: {}, timeout: {}", baseUrl, properties.readTimeout());
                return new LlmException("LLM 응답 시간(" + properties.readTimeout().toSeconds()
                        + "초)이 초과됐어요. 더 작은 모델을 쓰거나 llm.read-timeout을 늘려주세요.", e);
            }
        }
        log.warn("LLM 호출 실패 - baseUrl: {}", baseUrl, e);
        return new LlmException("LLM 호출 중 오류가 발생했어요. (" + e.getMostSpecificCause().getMessage() + ")", e);
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
