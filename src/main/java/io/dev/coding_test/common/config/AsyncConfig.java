package io.dev.coding_test.common.config;

import io.dev.coding_test.llm.config.LlmProperties;
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
     * 여러 회원의 LLM 서버를 동시에 처리하도록 {@code llm.max-parallel}개의 스레드를 둔다.
     * LLM 서버별 동시 실행 수와 대기열 크기는 {@code LlmServerQueue}가 제한하므로 이 실행기에는 서버마다
     * {@code llm.concurrency}개까지만 들어오며, 실행기 자체의 대기열은 제한하지 않는다.
     * 종료 시 진행 중인 요약은 기다리지 않으며, 남은 PENDING/PROCESSING 메모는 다음 기동 때 다시 요약한다.
     * </p>
     *
     * @param properties LLM 설정
     * @return 요약 작업 실행기
     */
    @Bean(name = LLM_EXECUTOR)
    public ThreadPoolTaskExecutor llmExecutor(LlmProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.maxParallel());
        executor.setMaxPoolSize(properties.maxParallel());
        executor.setThreadNamePrefix("llm-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
