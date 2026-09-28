package io.dev.coding_test.llm.config;

import io.dev.coding_test.llm.client.LlmClient;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.provider.lmstudio.LmStudioLlmClient;
import io.dev.coding_test.llm.provider.ollama.OllamaLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 로컬 LLM 클라이언트 설정.
 * <p>
 * {@code llm.provider} 값에 따라 {@link OllamaLlmClient} 또는 {@link LmStudioLlmClient} 하나만 등록한다.
 * 로컬/Tailscale 네트워크의 LLM 서버를 직접 호출하므로 시스템 프록시를 사용하지 않는다.
 * 요청 본문은 버퍼링해 {@code Content-Length}와 함께 보낸다. (chunked 요청을 처리하지 못하는 OpenAI 호환 서버 대비)
 * </p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public LlmClient llmClient(LlmProperties properties, SummaryResultParser parser) {
        RestClient restClient = restClient(properties);
        log.info("LLM 클라이언트 설정 - provider: {}, baseUrl: {}, model: {}",
                properties.provider(), properties.resolvedBaseUrl(), properties.model());
        return switch (properties.provider()) {
            case OLLAMA -> new OllamaLlmClient(restClient, properties, parser);
            case LMSTUDIO -> new LmStudioLlmClient(restClient, properties, parser);
        };
    }

    private RestClient restClient(LlmProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .baseUrl(properties.resolvedBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasApiKey()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return builder.build();
    }
}
