package io.dev.coding_test.llm.queue;

import io.dev.coding_test.llm.config.LlmProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class LlmServerQueueTest {

    private static final String SERVER_A = "192.168.0.10:1234";
    private static final String SERVER_B = "192.168.0.20:11434";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ThreadPoolTaskExecutor executor = executor(4);
    /** 서버당 동시 1개, 대기 2개 */
    private final LlmServerQueue queue =
            new LlmServerQueue(executor, new LlmProperties(0.2, null, null, null, null, 1, 2, 4));

    private final CountDownLatch gate = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        gate.countDown();
        executor.shutdown();
    }

    @Test
    void 한_서버가_막혀_있어도_다른_서버의_작업은_바로_실행한다() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        queue.submit(SERVER_A, this::block);
        queue.submit(SERVER_B, done::countDown);

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(queue.size(SERVER_A)).isEqualTo(1);
    }

    @Test
    void 같은_서버의_작업은_동시_실행_수만큼만_실행하고_순서대로_처리한다() {
        List<Integer> order = new CopyOnWriteArrayList<>();

        queue.submit(SERVER_A, () -> {
            block();
            order.add(1);
        });
        queue.submit(SERVER_A, () -> order.add(2));
        queue.submit(SERVER_A, () -> order.add(3));

        assertThat(queue.size(SERVER_A)).isEqualTo(3);
        assertThat(order).isEmpty();

        gate.countDown();
        await().atMost(TIMEOUT).until(() -> order.size() == 3);
        assertThat(order).containsExactly(1, 2, 3);
    }

    @Test
    void 서버의_대기열이_가득_차면_거부하고_다른_서버는_계속_받는다() {
        queue.submit(SERVER_A, this::block);
        queue.submit(SERVER_A, () -> { });
        queue.submit(SERVER_A, () -> { });

        assertThatThrownBy(() -> queue.submit(SERVER_A, () -> { }))
                .isInstanceOf(TaskRejectedException.class);
        queue.submit(SERVER_B, () -> { });
    }

    @Test
    void 작업이_끝난_서버의_대기열은_지운다() {
        queue.submit(SERVER_A, this::block);
        queue.submit(SERVER_A, () -> { });
        queue.submit(SERVER_B, () -> { });

        gate.countDown();

        await().atMost(TIMEOUT).until(() -> queue.serverCount() == 0);
        assertThat(queue.size(SERVER_A)).isZero();
    }

    @Test
    void 작업이_예외로_끝나도_다음_작업을_실행한다() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        queue.submit(SERVER_A, () -> {
            throw new IllegalStateException("요약 실패");
        });
        queue.submit(SERVER_A, done::countDown);

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 실행기가_종료되면_거부하고_대기열을_남기지_않는다() {
        executor.shutdown();

        assertThatThrownBy(() -> queue.submit(SERVER_A, () -> { }))
                .isInstanceOf(TaskRejectedException.class);
        assertThat(queue.serverCount()).isZero();
    }

    // ===================== 회원당 동시 실행 1건 =====================

    @Test
    void 회원_한_명은_서버가_달라도_동시에_1건만_실행한다() throws InterruptedException {
        CountDownLatch secondRan = new CountDownLatch(1);

        queue.submit(SERVER_A, 1L, this::block);
        queue.submit(SERVER_B, 1L, secondRan::countDown);

        assertThat(secondRan.await(300, TimeUnit.MILLISECONDS)).as("같은 회원의 두 번째 요약은 기다린다").isFalse();
        assertThat(queue.runningUserCount()).isEqualTo(1);

        gate.countDown();
        assertThat(secondRan.await(5, TimeUnit.SECONDS)).as("첫 요약이 끝나면 다른 서버 대기열의 두 번째 요약이 실행된다").isTrue();
    }

    @Test
    void 같은_회원의_요약이_기다리는_동안_다른_회원의_요약이_먼저_실행된다() throws InterruptedException {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch otherRan = new CountDownLatch(1);

        queue.submit(SERVER_A, 1L, () -> {
            block();
            order.add("A1");
        });
        queue.submit(SERVER_B, 1L, () -> order.add("A2"));
        queue.submit(SERVER_B, 2L, () -> {
            order.add("B1");
            otherRan.countDown();
        });

        assertThat(otherRan.await(5, TimeUnit.SECONDS)).as("서버 B의 앞줄에 회원 1이 기다려도 회원 2는 바로 실행").isTrue();
        assertThat(order).containsExactly("B1");

        gate.countDown();
        await().atMost(TIMEOUT).until(() -> order.size() == 3);
        assertThat(order).containsExactly("B1", "A1", "A2");
    }

    @Test
    void 회원의_작업이_끝나면_회원_수_기록을_지운다() {
        queue.submit(SERVER_A, 1L, () -> { });
        queue.submit(SERVER_A, 1L, () -> { });

        await().atMost(TIMEOUT).until(() -> queue.serverCount() == 0);
        assertThat(queue.runningUserCount()).isZero();
    }

    @Test
    void 작업이_예외로_끝나도_회원의_다음_작업을_실행한다() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        queue.submit(SERVER_A, 1L, () -> {
            throw new IllegalStateException("요약 실패");
        });
        queue.submit(SERVER_B, 1L, done::countDown);

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 실행기가_종료돼도_회원_수_기록이_남지_않는다() {
        executor.shutdown();

        assertThatThrownBy(() -> queue.submit(SERVER_A, 1L, () -> { })).isInstanceOf(TaskRejectedException.class);
        assertThat(queue.runningUserCount()).isZero();
        assertThat(queue.serverCount()).isZero();
    }

    private void block() {
        try {
            gate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolTaskExecutor executor(int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.initialize();
        return executor;
    }
}
