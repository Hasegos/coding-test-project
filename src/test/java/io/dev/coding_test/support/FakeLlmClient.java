package io.dev.coding_test.support;

import io.dev.coding_test.llm.client.LlmClient;
import io.dev.coding_test.llm.dto.SummaryResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 테스트용 가짜 LLM 클라이언트.
 * <p>
 * 테스트 컨텍스트에서 실제 Ollama/LM Studio 클라이언트 대신 주입되며({@code @Primary}),
 * 응답/예외를 테스트에서 지정하고 호출을 일시 정지시켜 동시성 시나리오를 재현할 수 있다.
 * </p>
 */
@Primary
@Component
public class FakeLlmClient implements LlmClient {

    public static final String MODEL = "fake-model";

    private volatile BiFunction<String, String, SummaryResult> behavior = FakeLlmClient::defaultResult;
    private volatile CountDownLatch entered = new CountDownLatch(0);
    private volatile CountDownLatch gate = new CountDownLatch(0);
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public SummaryResult summarize(String title, String content) {
        calls.incrementAndGet();
        entered.countDown();
        try {
            if (!gate.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("FakeLlmClient gate timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return behavior.apply(title, content);
    }

    @Override
    public String model() {
        return MODEL;
    }

    /** 요약 결과(또는 예외)를 지정한다. */
    public void willReturn(BiFunction<String, String, SummaryResult> behavior) {
        this.behavior = behavior;
    }

    /**
     * 다음 호출을 {@link #release()} 전까지 멈춘다.
     *
     * @return 호출이 시작되면 0이 되는 latch
     */
    public CountDownLatch holdNextCall() {
        this.entered = new CountDownLatch(1);
        this.gate = new CountDownLatch(1);
        return entered;
    }

    public void release() {
        gate.countDown();
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        behavior = FakeLlmClient::defaultResult;
        gate.countDown();
        entered = new CountDownLatch(0);
        gate = new CountDownLatch(0);
        calls.set(0);
    }

    private static SummaryResult defaultResult(String title, String content) {
        return new SummaryResult(title + " 요약: " + content, List.of(title + " 후속 작업하기"));
    }
}
