package io.dev.coding_test.repository;

import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.model.MemoTodo;
import io.dev.coding_test.service.MemoService;
import io.dev.coding_test.service.MemoSummaryService;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestMembers;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 경로별 실행 SQL 수와 엔티티 로딩 수를 검증한다. (메모 12개 × 할 일 3개, 본문 약 5,000자)
 * <p>
 * 최적화 전: 목록 3쿼리·엔티티 48개 로딩(본문 전체 + 할 일 컬렉션), 상세 2쿼리, 요약 상태 폴링 2쿼리.
 * </p>
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class MemoQueryCountTest {

    @Autowired
    private TestMembers testMembers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long memberId;

    @Autowired
    private MemoService memoService;

    @Autowired
    private MemoSummaryService memoSummaryService;

    @Autowired
    private MemoRepository memoRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private final List<Long> memoIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        memberId = testMembers.login("tester").getMemberId();
        for (int i = 0; i < 12; i++) {
            MemoResponse saved = memoService.create(memberId, new MemoRequest("회의 " + i, "본문 ".repeat(1_000)));
            Memo memo = memoRepository.findById(saved.memoId()).orElseThrow();
            for (int t = 0; t < 3; t++) {
                MemoTodo todo = new MemoTodo();
                todo.setMemo(memo);
                todo.setContent("할 일 " + t);
                todo.setSortOrder(t);
                memo.getTodos().add(todo);
            }
            memoIds.add(saved.memoId());
        }
        entityManager.flush();
        entityManager.clear();
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Test
    void 목록은_projection_1쿼리와_개수_1쿼리로_엔티티를_로딩하지_않는다() {
        Page<MemoListItem> page = memoService.getMemos(memberId, null, 0, 12);

        assertThat(page.getContent()).hasSize(12);
        assertThat(page.getContent().getFirst().todoCount()).isEqualTo(3);
        assertQueries(2, 0);
    }

    @Test
    void 검색도_projection_1쿼리와_개수_1쿼리로_처리한다() {
        Page<MemoListItem> page = memoService.getMemos(memberId, "회의", 0, 12);

        assertThat(page.getTotalElements()).isEqualTo(12);
        assertQueries(2, 0);
    }

    @Test
    void 상세는_할_일과_함께_1쿼리로_조회한다() {
        MemoResponse memo = memoService.getMemo(memberId, memoIds.getFirst());

        assertThat(memo.summary().todos()).hasSize(3);
        assertQueries(1, 4);
    }

    @Test
    void 요약_상태_폴링은_상태_컬럼만_1쿼리로_조회한다() {
        memoSummaryService.getSummaryStatus(memberId, memoIds.getFirst());

        assertQueries(1, 0);
    }

    @Test
    void 요약_결과_조회는_할_일과_함께_1쿼리로_조회한다() {
        assertThat(memoSummaryService.getSummary(memberId, memoIds.getFirst()).todos()).hasSize(3);

        assertQueries(1, 4);
    }

    private void assertQueries(long statements, long entities) {
        assertThat(statistics.getPrepareStatementCount()).as("실행 SQL 수").isEqualTo(statements);
        assertThat(statistics.getEntityLoadCount()).as("로딩된 엔티티 수").isEqualTo(entities);
    }
}
