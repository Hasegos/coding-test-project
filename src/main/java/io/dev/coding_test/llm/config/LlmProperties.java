package io.dev.coding_test.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로컬 LLM 호출 공통 설정 ({@code llm.*}).
 * <p>
 * LLM 서버 주소·모델은 사용자가 LLM 설정 화면에서 입력하며(DB 저장), 여기에는 호출 방식만 둔다.
 * </p>
 *
 * @param temperature    생성 온도 (요약은 낮을수록 안정적)
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 대기 타임아웃 (로컬 모델은 수십 초 걸릴 수 있음)
 * @param concurrency    동시에 처리할 요약 작업 수 (GPU 1장이면 1 권장)
 * @param queueCapacity  요약 대기열 크기, 초과 시 해당 요약은 실패 처리
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(double temperature,
                            Duration connectTimeout,
                            Duration readTimeout,
                            int concurrency,
                            int queueCapacity) {

    public LlmProperties {
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (concurrency < 1) concurrency = 1;
        if (queueCapacity < 1) queueCapacity = 100;
    }
}
