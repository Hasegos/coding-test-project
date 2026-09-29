package io.dev.coding_test.llm.queue;

import io.dev.coding_test.common.config.AsyncConfig;
import io.dev.coding_test.llm.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * LLM 서버별 요약 대기열.
 * <p>
 * 요약은 메모 작성자의 LLM 서버로 보내므로 회원마다 서버가 다르다. 모든 요약을 한 줄로 세우면
 * 한 회원의 서버가 느리거나 꺼져 있을 때(최대 {@code llm.read-timeout}) 다른 회원의 요약까지 밀리므로 서버마다 따로 줄을 세운다.
 * </p>
 * <ul>
 *     <li>서버 하나에는 {@code llm.concurrency}개까지만 동시에 보낸다. (GPU 1장이면 1 — 같은 서버를 쓰는 회원끼리는 순서대로 처리)</li>
 *     <li><b>회원 한 명은 동시에 1건만</b> 실행한다. 서버 주소를 바꿔 가며 여러 서버에 동시에 요청을 걸어 두는 것을 막고,
 *         이미 실행 중인 회원의 다음 요약은 기다리는 동안 다른 회원의 요약이 먼저 실행된다.</li>
 *     <li>서버마다 {@code llm.queue-capacity}개까지 기다리고, 넘치면 {@link TaskRejectedException}을 던진다.
 *         한 회원이 대기열을 채워도 다른 서버의 요약은 받는다.</li>
 *     <li>실제 실행은 공용 실행기({@code llm.max-parallel}개 스레드)가 맡아 서로 다른 서버를 동시에 처리한다.</li>
 *     <li>처리할 작업이 없는 서버의 대기열은 지워 메모리에 남기지 않는다.</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmServerQueue {

    private final TaskExecutor executor;
    private final int concurrency;
    private final int capacity;

    /** 서버 주소 → 대기열 (lanes 자체를 잠금으로 사용, 실행 중인 회원 목록도 이 잠금으로 보호) */
    private final Map<String, Lane> lanes = new HashMap<>();
    private final Set<Long> runningUsers = new HashSet<>();

    public LlmServerQueue(@Qualifier(AsyncConfig.LLM_EXECUTOR) TaskExecutor executor, LlmProperties properties) {
        this.executor = executor;
        this.concurrency = properties.concurrency();
        this.capacity = properties.queueCapacity();
    }

    /**
     * 회원 구분 없이 작업을 서버의 대기열에 넣는다.
     *
     * @param serverKey LLM 서버 식별값 (예: {@code 192.168.0.10:1234})
     * @param task      실행할 작업
     * @throws TaskRejectedException 서버의 대기열이 가득 찼거나 실행기가 종료된 경우
     */
    public void submit(String serverKey, Runnable task) {
        submit(serverKey, null, task);
    }

    /**
     * 작업을 서버의 대기열에 넣는다. 서버의 동시 실행 수와 회원의 동시 실행 수에 여유가 있으면 바로 실행한다.
     *
     * @param serverKey LLM 서버 식별값 (예: {@code 192.168.0.10:1234})
     * @param userId    요약을 요청한 회원 ID, {@code null}이면 회원별 제한을 적용하지 않는다
     * @param task      실행할 작업
     * @throws TaskRejectedException 서버의 대기열이 가득 찼거나 실행기가 종료된 경우
     */
    public void submit(String serverKey, Long userId, Runnable task) {
        synchronized (lanes) {
            Lane lane = lanes.computeIfAbsent(serverKey, Lane::new);
            Entry entry = new Entry(userId, task);
            if (canStart(lane, entry)) {
                start(lane, entry);
                return;
            }
            if (lane.waiting.size() >= capacity) {
                throw new TaskRejectedException("LLM 서버 대기열 초과 - " + serverKey);
            }
            lane.waiting.add(entry);
        }
    }

    /**
     * 서버의 실행 중 + 대기 중인 작업 수.
     *
     * @param serverKey LLM 서버 식별값
     * @return 작업 수, 대기열이 없으면 0
     */
    public int size(String serverKey) {
        synchronized (lanes) {
            Lane lane = lanes.get(serverKey);
            return lane == null ? 0 : lane.running + lane.waiting.size();
        }
    }

    /**
     * 작업이 남아 있는 서버 수.
     *
     * @return 서버 수
     */
    public int serverCount() {
        synchronized (lanes) {
            return lanes.size();
        }
    }

    /**
     * 지금 실행 중인 회원 수.
     *
     * @return 회원 수
     */
    public int runningUserCount() {
        synchronized (lanes) {
            return runningUsers.size();
        }
    }

    private boolean canStart(Lane lane, Entry entry) {
        return lane.running < concurrency && (entry.userId == null || !runningUsers.contains(entry.userId));
    }

    /**
     * 공용 실행기에 작업을 넘긴다. ({@code lanes} 잠금 안에서 호출)
     */
    private void start(Lane lane, Entry entry) {
        lane.running++;
        if (entry.userId != null) {
            runningUsers.add(entry.userId);
        }
        try {
            executor.execute(() -> {
                try {
                    entry.task.run();
                } finally {
                    finish(lane, entry);
                }
            });
        } catch (TaskRejectedException e) {
            release(lane, entry);
            throw e;
        }
    }

    /** 작업이 끝나면 자리를 비우고, 실행할 수 있게 된 대기 작업을 이어서 넘긴다. */
    private void finish(Lane lane, Entry entry) {
        synchronized (lanes) {
            release(lane, entry);
            startWaiting();
        }
    }

    private void release(Lane lane, Entry entry) {
        lane.running--;
        if (entry.userId != null) {
            runningUsers.remove(entry.userId);
        }
        removeIfIdle(lane);
    }

    /**
     * 모든 서버의 대기열에서 지금 시작할 수 있는 작업을 순서대로 시작한다.
     * 회원이 끝낸 작업은 다른 서버의 대기열에 있는 그 회원의 작업도 시작할 수 있게 만들므로 모든 대기열을 확인한다.
     */
    private void startWaiting() {
        for (Lane lane : new ArrayList<>(lanes.values())) {
            Iterator<Entry> waiting = lane.waiting.iterator();
            while (lane.running < concurrency && waiting.hasNext()) {
                Entry next = waiting.next();
                if (!canStart(lane, next)) {
                    continue;
                }
                waiting.remove();
                try {
                    start(lane, next);
                } catch (TaskRejectedException e) {
                    // 종료 중: 남은 요약은 PENDING으로 남아 다음 기동 때 다시 요청된다.
                    log.warn("LLM 실행기 종료 중 - 대기 중인 요약 {}건 중단 ({})", lane.waiting.size() + 1, lane.key);
                    lane.waiting.clear();
                    removeIfIdle(lane);
                    break;
                }
            }
        }
    }

    private void removeIfIdle(Lane lane) {
        if (lane.running == 0 && lane.waiting.isEmpty()) {
            lanes.remove(lane.key, lane);
        }
    }

    private record Entry(Long userId, Runnable task) {
    }

    private static final class Lane {
        private final String key;
        private final Queue<Entry> waiting = new ArrayDeque<>();
        private int running;

        private Lane(String key) {
            this.key = key;
        }
    }
}
