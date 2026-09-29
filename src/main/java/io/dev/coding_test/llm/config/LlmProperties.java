package io.dev.coding_test.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * 로컬 LLM 호출 공통 설정 ({@code llm.*}).
 * <p>
 * LLM 서버 주소·모델은 사용자가 LLM 설정 화면에서 입력하며(DB 저장), 여기에는 호출 방식만 둔다.
 * </p>
 *
 * @param temperature     생성 온도 (요약은 낮을수록 안정적)
 * @param connectTimeout  연결 타임아웃
 * @param readTimeout     요약 요청의 전체 제한 시간 (응답 본문을 다 받을 때까지, 로컬 모델은 수십 초 걸릴 수 있음)
 * @param modelsTimeout   모델 목록 조회(연결 테스트)의 전체 제한 시간
 * @param maxResponseSize 응답 본문 최대 크기, 초과하면 읽기를 중단한다
 * @param concurrency     LLM 서버 하나에 동시에 보낼 요약 수 (GPU 1장이면 1 권장)
 * @param queueCapacity   LLM 서버 하나의 요약 대기열 크기, 초과 시 해당 요약은 실패 처리
 * @param maxParallel     전체 동시 요약 수 (여러 회원의 LLM 서버를 동시에 처리하는 스레드 수)
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(double temperature,
                            Duration connectTimeout,
                            Duration readTimeout,
                            Duration modelsTimeout,
                            DataSize maxResponseSize,
                            int concurrency,
                            int queueCapacity,
                            int maxParallel) {

    public LlmProperties {
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (modelsTimeout == null) modelsTimeout = Duration.ofSeconds(15);
        if (maxResponseSize == null) maxResponseSize = DataSize.ofMegabytes(1);
        if (concurrency < 1) concurrency = 1;
        if (queueCapacity < 1) queueCapacity = 100;
        if (maxParallel < 1) maxParallel = 8;
    }
}
