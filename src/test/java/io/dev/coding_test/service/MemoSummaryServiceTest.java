package io.dev.coding_test.service;

import io.dev.coding_test.dto.memo.MemoRequest;
import io.dev.coding_test.dto.memo.MemoResponse;
import io.dev.coding_test.dto.setting.LlmSettingRequest;
import io.dev.coding_test.dto.summary.MemoSummaryResponse;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.exception.LlmUnavailableException;
import io.dev.coding_test.llm.queue.LlmServerBreaker;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.model.enums.SummaryStatus;
import io.dev.coding_test.repository.LlmSettingRepository;
import io.dev.coding_test.repository.MemoRepository;
import io.dev.coding_test.repository.UserRepository;
import io.dev.coding_test.support.FakeLlmClient;
import io.dev.coding_test.support.FakeLlmClientFactory;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 저장 → 커밋 후 이벤트 → LLM 실행기 → 결과 반영까지 비동기 요약 흐름을 검증한다.
 * 실제 커밋이 일어나야 이벤트가 발행되므로 {@code @Transactional}을 사용하지 않고 테스트마다 데이터를 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MemoSummaryServiceTest {

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long userId;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private MemoService memoService;

    @Autowired
    private MemoSummaryService memoSummaryService;

    @Autowired
    private MemoRepository memoRepository;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private FakeLlmClientFactory fakeLlmClientFactory;

    @Autowired
    private LlmServerBreaker llmServerBreaker;

    @Autowired
    private LlmSettingService llmSettingService;

    @Autowired
    private LlmSettingRepository llmSettingRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userId = testUsers.login("tester").getUserId();
        llmServerBreaker.clear();
        fakeLlmClient.reset();
        saveSetting();
    }

    @AfterEach
    void tearDown() {
        llmServerBreaker.clear();
        testLoginContext.reset();
        fakeLlmClient.reset();
        memoRepository.deleteAll();
        llmSettingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 메모를_저장하면_비동기로_요약과_할_일이_저장된다() {
        fakeLlmClient.willReturn((title, content) ->
                new SummaryResult("배포 일정을 논의했다.", List.of("배포 스크립트 점검하기", "QA 일정 공유하기")));

        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));
        assertThat(memo.summary().status()).isEqualTo(SummaryStatus.PENDING);

        MemoSummaryResponse summary = awaitStatus(memo.memoId(), SummaryStatus.DONE);
        assertThat(summary.summary()).isEqualTo("배포 일정을 논의했다.");
        assertThat(summary.todos()).containsExactly("배포 스크립트 점검하기", "QA 일정 공유하기");
        assertThat(summary.model()).isEqualTo(FakeLlmClient.MODEL);
        assertThat(summary.summarizedAt()).isNotNull();
        assertThat(summary.error()).isNull();
    }

    @Test
    void 요약_결과_반영은_메모_수정일을_바꾸지_않는다() {
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));
        awaitStatus(memo.memoId(), SummaryStatus.DONE);

        assertThat(memoService.getMemo(userId, memo.memoId()).updatedAt()).isEqualTo(memo.updatedAt());
    }

    @Test
    void LLM_호출이_실패하면_FAILED와_실패_사유를_저장한다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new LlmException("로컬 LLM 서버(http://localhost:11434)에 연결할 수 없어요.");
        });

        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        MemoSummaryResponse summary = awaitStatus(memo.memoId(), SummaryStatus.FAILED);
        assertThat(summary.error()).contains("연결할 수 없어요");
        assertThat(summary.summary()).isNull();
    }

    @Test
    void 예상치_못한_예외도_FAILED로_기록한다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new IllegalStateException("boom");
        });

        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        assertThat(awaitStatus(memo.memoId(), SummaryStatus.FAILED).error()).isEqualTo("요약 중 알 수 없는 오류가 발생했어요.");
    }

    @Test
    void 실패한_요약을_재요청하면_다시_요약한다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new LlmException("일시적 오류");
        });
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));
        awaitStatus(memo.memoId(), SummaryStatus.FAILED);

        fakeLlmClient.willReturn((title, content) -> new SummaryResult("재요약 성공", List.of()));
        MemoSummaryResponse retried = memoSummaryService.retry(userId, memo.memoId());

        assertThat(retried.status()).isEqualTo(SummaryStatus.PENDING);
        MemoSummaryResponse summary = awaitStatus(memo.memoId(), SummaryStatus.DONE);
        assertThat(summary.summary()).isEqualTo("재요약 성공");
        assertThat(summary.todos()).isEmpty();
        assertThat(fakeLlmClient.calls()).isEqualTo(2);
    }

    @Test
    void 요약_중에는_재요청해도_중복_호출하지_않는다() throws InterruptedException {
        CountDownLatch entered = fakeLlmClient.holdNextCall();
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        MemoSummaryResponse retried = memoSummaryService.retry(userId, memo.memoId());
        fakeLlmClient.release();

        assertThat(retried.status()).isEqualTo(SummaryStatus.PROCESSING);
        awaitStatus(memo.memoId(), SummaryStatus.DONE);
        assertThat(fakeLlmClient.calls()).isEqualTo(1);
    }

    @Test
    void 요약_중_메모가_수정되면_이전_결과는_버리고_새_내용으로_다시_요약한다() throws InterruptedException {
        fakeLlmClient.willReturn((title, content) -> new SummaryResult("요약: " + content, List.of()));
        CountDownLatch entered = fakeLlmClient.holdNextCall();
        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "초안 본문"));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        memoService.update(userId, memo.memoId(), new MemoRequest("회의", "최종 본문"));
        fakeLlmClient.release();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            MemoSummaryResponse summary = memoSummaryService.getSummary(userId, memo.memoId());
            assertThat(summary.status()).isEqualTo(SummaryStatus.DONE);
            assertThat(summary.summary()).isEqualTo("요약: 최종 본문");
        });
        assertThat(fakeLlmClient.calls()).isEqualTo(2);
    }

    @Test
    void 내용이_바뀌지_않은_수정은_다시_요약하지_않는다() {
        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "본문"));
        awaitStatus(memo.memoId(), SummaryStatus.DONE);

        memoService.update(userId, memo.memoId(), new MemoRequest(" 회의 ", "본문 "));

        assertThat(memoSummaryService.getSummary(userId, memo.memoId()).status()).isEqualTo(SummaryStatus.DONE);
        assertThat(fakeLlmClient.calls()).isEqualTo(1);
    }

    @Test
    void 요약_중_메모가_삭제되어도_오류없이_결과를_버린다() throws InterruptedException {
        CountDownLatch entered = fakeLlmClient.holdNextCall();
        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "본문"));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        memoService.delete(userId, memo.memoId());
        fakeLlmClient.release();

        await().pollDelay(Duration.ofMillis(200)).atMost(TIMEOUT)
                .until(() -> !memoRepository.existsById(memo.memoId()));
        assertThat(memoRepository.count()).isZero();
    }

    @Test
    void 메모를_삭제하면_할_일도_함께_삭제된다() {
        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "본문"));
        assertThat(awaitStatus(memo.memoId(), SummaryStatus.DONE).todos()).hasSize(1);

        memoService.delete(userId, memo.memoId());

        assertThat(memoRepository.count()).isZero();
    }

    private MemoSummaryResponse awaitStatus(Long memoId, SummaryStatus status) {
        await().atMost(TIMEOUT).until(() -> memoSummaryService.getSummary(userId, memoId).status() == status);
        return memoSummaryService.getSummary(userId, memoId);
    }

    @Test
    void 저장된_LLM_설정의_접속_정보로_요약한다() {
        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "본문"));
        awaitStatus(memo.memoId(), SummaryStatus.DONE);

        LlmConnection connection = fakeLlmClientFactory.lastConnection();
        assertThat(connection.provider()).isEqualTo(LlmProvider.LMSTUDIO);
        assertThat(connection.baseUrl()).isEqualTo("http://100.66.180.73:1234");
        assertThat(connection.model()).isEqualTo("qwen2.5-7b-instruct");
    }

    @Test
    void LLM이_설정되지_않았으면_설정_안내와_함께_FAILED로_기록한다() {
        llmSettingRepository.deleteAll();

        MemoResponse memo = memoService.create(userId, new MemoRequest("회의", "본문"));

        MemoSummaryResponse summary = awaitStatus(memo.memoId(), SummaryStatus.FAILED);
        assertThat(summary.error()).isEqualTo(MemoSummaryService.NOT_CONFIGURED_MESSAGE);
        assertThat(fakeLlmClient.calls()).isZero();
    }

    @Test
    void LLM_설정_후_실패한_요약을_모두_다시_요청한다() {
        llmSettingRepository.deleteAll();
        MemoResponse first = memoService.create(userId, new MemoRequest("회의", "본문"));
        MemoResponse second = memoService.create(userId, new MemoRequest("스터디", "본문"));
        awaitStatus(first.memoId(), SummaryStatus.FAILED);
        awaitStatus(second.memoId(), SummaryStatus.FAILED);

        saveSetting();
        int retried = memoSummaryService.retryFailed(userId);

        assertThat(retried).isEqualTo(2);
        awaitStatus(first.memoId(), SummaryStatus.DONE);
        awaitStatus(second.memoId(), SummaryStatus.DONE);
    }

    @Test
    void 요약은_메모_작성자의_LLM_설정으로_실행한다() {
        Long otherId = testUsers.create("other").getUserId();
        llmSettingService.save(otherId, new LlmSettingRequest(LlmProvider.OLLAMA, "100.100.0.20", 11434,
                "llama3.2:3b", null, false));

        MemoResponse memo = memoService.create(otherId, new MemoRequest("다른 회원 메모", "본문"));
        await().atMost(TIMEOUT).until(() -> memoSummaryService.getSummary(otherId, memo.memoId()).status() == SummaryStatus.DONE);

        LlmConnection connection = fakeLlmClientFactory.lastConnection();
        assertThat(connection.provider()).isEqualTo(LlmProvider.OLLAMA);
        assertThat(connection.baseUrl()).isEqualTo("http://100.100.0.20:11434");
        assertThat(connection.model()).isEqualTo("llama3.2:3b");
    }

    @Test
    void 다른_회원이_LLM을_설정했어도_작성자가_설정하지_않았으면_FAILED로_기록한다() {
        Long otherId = testUsers.create("other").getUserId();

        MemoResponse memo = memoService.create(otherId, new MemoRequest("회의", "본문"));

        await().atMost(TIMEOUT).until(() -> memoSummaryService.getSummary(otherId, memo.memoId()).status() == SummaryStatus.FAILED);
        assertThat(memoSummaryService.getSummary(otherId, memo.memoId()).error())
                .isEqualTo(MemoSummaryService.NOT_CONFIGURED_MESSAGE);
        assertThat(fakeLlmClient.calls()).isZero();
    }

    @Test
    void 실패한_요약_재요청은_설정을_저장한_회원의_메모만_다시_요청한다() {
        Long otherId = testUsers.create("other").getUserId();
        llmSettingRepository.deleteAll();
        MemoResponse mine = memoService.create(userId, new MemoRequest("내 메모", "본문"));
        MemoResponse others = memoService.create(otherId, new MemoRequest("다른 회원 메모", "본문"));
        awaitStatus(mine.memoId(), SummaryStatus.FAILED);
        await().atMost(TIMEOUT).until(() -> memoSummaryService.getSummary(otherId, others.memoId()).status() == SummaryStatus.FAILED);

        saveSetting();
        int retried = memoSummaryService.retryFailed(userId);

        assertThat(retried).isEqualTo(1);
        awaitStatus(mine.memoId(), SummaryStatus.DONE);
        assertThat(memoSummaryService.getSummary(otherId, others.memoId()).status()).isEqualTo(SummaryStatus.FAILED);
    }

    @Test
    void 한_회원의_LLM_서버가_느려도_다른_서버를_쓰는_회원의_요약은_기다리지_않는다() throws InterruptedException {
        Long otherId = testUsers.create("other").getUserId();
        llmSettingService.save(otherId, new LlmSettingRequest(LlmProvider.OLLAMA, "100.100.0.20", 11434,
                "llama3.2:3b", null, false));
        CountDownLatch slowEntered = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        fakeLlmClient.willReturn((title, content) -> {
            if (title.equals("느린 서버")) {
                slowEntered.countDown();
                try {
                    releaseSlow.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new SummaryResult("요약", List.of());
        });

        try {
            MemoResponse slow = memoService.create(userId, new MemoRequest("느린 서버", "본문"));
            assertThat(slowEntered.await(5, TimeUnit.SECONDS)).isTrue();
            MemoResponse other = memoService.create(otherId, new MemoRequest("다른 서버", "본문"));

            await().atMost(TIMEOUT).until(() ->
                    memoSummaryService.getSummary(otherId, other.memoId()).status() == SummaryStatus.DONE);
            assertThat(memoSummaryService.getSummary(userId, slow.memoId()).status()).isEqualTo(SummaryStatus.PROCESSING);
        } finally {
            releaseSlow.countDown();
        }
    }

    @Test
    void 서버가_연속으로_응답하지_않으면_쉬는_동안_요청하지_않고_바로_실패_처리한다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new LlmUnavailableException("LLM 응답 시간(120초)이 초과됐어요.", null);
        });

        for (int i = 1; i <= 3; i++) {
            MemoResponse memo = memoService.create(userId, new MemoRequest("실패 " + i, "본문"));
            awaitStatus(memo.memoId(), SummaryStatus.FAILED);
        }
        assertThat(fakeLlmClient.calls()).isEqualTo(3);

        MemoResponse resting = memoService.create(userId, new MemoRequest("쉬는 중", "본문"));
        awaitStatus(resting.memoId(), SummaryStatus.FAILED);

        assertThat(fakeLlmClient.calls()).as("쉬는 서버에는 요청하지 않는다").isEqualTo(3);
        assertThat(memoSummaryService.getSummary(userId, resting.memoId()).error())
                .contains("잠시 쉬고 있어요").contains("LLM 설정에서 저장하면");
    }

    @Test
    void 쉬는_중이어도_LLM_설정을_저장하면_다음_요약을_바로_시험하고_성공하면_정상으로_돌아온다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new LlmUnavailableException("연결할 수 없어요.", null);
        });
        for (int i = 1; i <= 3; i++) {
            MemoResponse memo = memoService.create(userId, new MemoRequest("실패 " + i, "본문"));
            awaitStatus(memo.memoId(), SummaryStatus.FAILED);
        }
        fakeLlmClient.reset();

        saveSetting();
        MemoResponse memo = memoService.create(userId, new MemoRequest("서버를 켠 뒤", "본문"));

        awaitStatus(memo.memoId(), SummaryStatus.DONE);
        MemoResponse next = memoService.create(userId, new MemoRequest("정상", "본문"));
        awaitStatus(next.memoId(), SummaryStatus.DONE);
    }

    @Test
    void 인증_실패나_서버_오류처럼_서버가_응답한_실패는_쉬게_하지_않는다() {
        fakeLlmClient.willReturn((title, content) -> {
            throw new LlmException("LLM 서버가 500 응답을 반환했어요.");
        });

        for (int i = 1; i <= 5; i++) {
            MemoResponse memo = memoService.create(userId, new MemoRequest("오류 " + i, "본문"));
            awaitStatus(memo.memoId(), SummaryStatus.FAILED);
        }

        assertThat(fakeLlmClient.calls()).as("5건 모두 서버에 요청").isEqualTo(5);
    }

    private void saveSetting() {
        llmSettingService.save(userId, new LlmSettingRequest(LlmProvider.LMSTUDIO, "100.66.180.73", 1234,
                "qwen2.5-7b-instruct", null, false));
    }
}
