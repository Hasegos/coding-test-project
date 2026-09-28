package io.dev.coding_test.common.config;

import io.dev.coding_test.llm.LlmProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업 실행기 설정.
 */
@Configuration
public class AsyncConfig {

    public static final String LLM_EXECUTOR = "llmExecutor";

    /**
     * 로컬 LLM 요약 전용 실행기.
     * <p>
     * 로컬 LLM은 GPU 자원을 공유하므로 동시 실행 수를 {@code llm.concurrency}로 제한하고,
     * 나머지 요청은 {@code llm.queue-capacity} 크기의 대기열에서 순서대로 처리한다.
     * 종료 시 진행 중인 요약은 기다리지 않으며, 남은 PENDING/PROCESSING 메모는 다음 기동 때 다시 요약한다.
     * </p>
     *
     * @param properties LLM 설정
     * @return 요약 작업 실행기
     */
    @Bean(name = LLM_EXECUTOR)
    public ThreadPoolTaskExecutor llmExecutor(LlmProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("llm-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
