package io.dev.coding_test.llm.config;

import io.dev.coding_test.model.enums.LlmProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로컬 LLM 연동 설정 ({@code llm.*}).
 *
 * @param provider       사용할 LLM 런타임 (ollama | lmstudio)
 * @param baseUrl        LLM 서버 주소, 비어 있으면 런타임별 기본 주소 사용 (Tailscale IP 등)
 * @param model          모델명 (예: Ollama {@code qwen2.5:7b}, LM Studio {@code qwen2.5-7b-instruct})
 * @param apiKey         인증 토큰, 비어 있으면 Authorization 헤더를 보내지 않음
 * @param temperature    생성 온도 (요약은 낮을수록 안정적)
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 대기 타임아웃 (로컬 모델은 수십 초 걸릴 수 있음)
 * @param concurrency    동시에 처리할 요약 작업 수 (GPU 1장이면 1 권장)
 * @param queueCapacity  요약 대기열 크기, 초과 시 해당 요약은 실패 처리
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(LlmProvider provider,
                            String baseUrl,
                            String model,
                            String apiKey,
                            double temperature,
                            Duration connectTimeout,
                            Duration readTimeout,
                            int concurrency,
                            int queueCapacity) {

    public LlmProperties {
        if (provider == null) provider = LlmProvider.OLLAMA;
        if (model == null || model.isBlank()) model = "qwen2.5:7b";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (concurrency < 1) concurrency = 1;
        if (queueCapacity < 1) queueCapacity = 100;
    }

    /**
     * 실제 호출할 LLM 서버 주소를 반환한다. 끝의 {@code /}는 제거한다.
     *
     * @return 설정된 주소, 없으면 런타임별 기본 주소
     */
    public String resolvedBaseUrl() {
        String url = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:" + provider.getDefaultPort() : baseUrl.strip();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
