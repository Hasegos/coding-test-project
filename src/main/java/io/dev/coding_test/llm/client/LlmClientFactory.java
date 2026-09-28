package io.dev.coding_test.llm.client;

import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.provider.lmstudio.LmStudioLlmClient;
import io.dev.coding_test.llm.provider.ollama.OllamaLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 설정 화면에서 저장한 접속 정보로 런타임별 {@link LlmClient}를 만든다.
 * <p>
 * 요약마다 HTTP 클라이언트를 새로 만들지 않도록 마지막으로 만든 클라이언트를 접속 정보 단위로 재사용하고,
 * 설정이 바뀌면(접속 정보가 달라지면) 새로 만든다.
 * </p>
 * <ul>
 *     <li>클라이언트를 돌려주기 전에 매번 {@link LlmHostGuard}로 주소를 다시 검사한다. (저장 후 규칙이 바뀐 경우 대비)</li>
 *     <li>로컬/Tailscale 네트워크의 LLM 서버를 직접 호출하므로 시스템 프록시를 사용하지 않는다.</li>
 *     <li>리다이렉트를 따라가지 않는다. (허용한 로컬 IP에서 다른 주소로 우회되는 것을 막음)</li>
 *     <li>요청 본문은 버퍼링해 {@code Content-Length}와 함께 보낸다. (chunked 요청을 처리하지 못하는 OpenAI 호환 서버 대비)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClientFactory {

    private final LlmProperties properties;
    private final SummaryResultParser parser;
    private final LlmHostGuard llmHostGuard;

    private final AtomicReference<CachedClient> cache = new AtomicReference<>();

    /**
     * 접속 정보에 맞는 LLM 클라이언트를 반환한다. 직전과 같은 접속 정보면 만들어 둔 클라이언트를 재사용한다.
     *
     * @param connection LLM 서버 접속 정보
     * @return LLM 클라이언트
     * @throws io.dev.coding_test.llm.exception.LlmException LLM 서버로 사용할 수 없는 주소인 경우
     */
    public LlmClient getClient(LlmConnection connection) {
        llmHostGuard.check(connection.host());
        CachedClient cached = cache.get();
        if (cached != null && cached.connection().equals(connection)) {
            return cached.client();
        }
        LlmClient client = create(connection);
        cache.set(new CachedClient(connection, client));
        log.info("LLM 클라이언트 생성 - {}", connection);
        return client;
    }

    /**
     * 접속 정보로 LLM 클라이언트를 새로 만든다. (연결 테스트처럼 저장 전 정보로 호출할 때 사용)
     *
     * @param connection LLM 서버 접속 정보
     * @return LLM 클라이언트
     * @throws io.dev.coding_test.llm.exception.LlmException LLM 서버로 사용할 수 없는 주소인 경우
     */
    public LlmClient create(LlmConnection connection) {
        llmHostGuard.check(connection.host());
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(properties.connectTimeout())
                .build();
        RestClient chatClient = restClient(httpClient, connection, properties.readTimeout());
        RestClient modelsClient = restClient(httpClient, connection, properties.modelsTimeout());
        return switch (connection.provider()) {
            case OLLAMA -> new OllamaLlmClient(chatClient, modelsClient, connection, properties, parser);
            case LMSTUDIO -> new LmStudioLlmClient(chatClient, modelsClient, connection, properties, parser);
        };
    }

    /**
     * @param timeout 요청 전체 제한 시간. JDK 요청 팩토리는 응답 본문을 다 읽을 때까지 이 시간을 적용한다.
     */
    private static RestClient restClient(HttpClient httpClient, LlmConnection connection, Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .baseUrl(connection.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (connection.hasApiKey()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + connection.apiKey());
        }
        return builder.build();
    }

    private record CachedClient(LlmConnection connection, LlmClient client) {
    }
}
