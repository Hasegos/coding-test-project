package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class MemoServiceTest {

    @Autowired
    private TestMembers testMembers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long memberId;

    @BeforeEach
    void loginMember() {
        memberId = testMembers.login("tester").getMemberId();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Autowired
    private MemoService memoService;

    @Autowired
    private MemoRepository memoRepository;

    @Test
    void 메모를_저장하면_앞뒤_공백을_제거하고_작성일을_기록한다() {
        MemoResponse saved = memoService.create(memberId, new MemoRequest("  주간 회의  ", "\n배포 일정 논의\n"));

        Memo memo = memoRepository.findById(saved.memoId()).orElseThrow();
        assertThat(memo.getTitle()).isEqualTo("주간 회의");
        assertThat(memo.getContent()).isEqualTo("배포 일정 논의");
        assertThat(memo.getCreatedAt()).isNotNull();
        assertThat(memo.getUpdatedAt()).isEqualTo(memo.getCreatedAt());
    }

    @Test
    void 메모_목록은_최신순으로_조회된다() {
        memoService.create(memberId, new MemoRequest("첫 번째", "본문"));
        memoService.create(memberId, new MemoRequest("두 번째", "본문"));

        Page<MemoListItem> page = memoService.getMemos(memberId, null, 0, 10);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(MemoListItem::title).containsExactly("두 번째", "첫 번째");
    }

    @Test
    void 키워드로_제목과_본문을_대소문자_구분없이_검색한다() {
        memoService.create(memberId, new MemoRequest("Spring 회의", "JPA 논의"));
        memoService.create(memberId, new MemoRequest("장보기", "우유, spring water"));
        memoService.create(memberId, new MemoRequest("운동", "스쿼트"));

        Page<MemoListItem> page = memoService.getMemos(memberId, "  SPRING ", 0, 10);

        assertThat(page.getContent()).extracting(MemoListItem::title)
                .containsExactlyInAnyOrder("Spring 회의", "장보기");
    }

    @Test
    void 검색_키워드의_퍼센트_문자는_와일드카드로_해석하지_않는다() {
        memoService.create(memberId, new MemoRequest("달성률 100%", "본문"));
        memoService.create(memberId, new MemoRequest("일반 메모", "본문"));

        assertThat(memoService.getMemos(memberId, "%", 0, 10).getContent())
                .extracting(MemoListItem::title).containsExactly("달성률 100%");
    }

    @Test
    void 페이지_번호와_크기는_허용_범위로_보정된다() {
        Page<MemoListItem> page = memoService.getMemos(memberId, null, -3, 1_000);

        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(MemoService.MAX_PAGE_SIZE);
    }

    @Test
    void 목록_미리보기는_공백을_합치고_길이를_제한한다() {
        memoService.create(memberId, new MemoRequest("긴 메모", "첫 줄\n\n   둘째 줄 " + "가".repeat(200)));

        String preview = memoService.getMemos(memberId, null, 0, 10).getContent().getFirst().preview();

        assertThat(preview).startsWith("첫 줄 둘째 줄 ");
        assertThat(preview).hasSize(MemoListItem.PREVIEW_LENGTH + 1).endsWith("…");
    }

    @Test
    void 존재하지_않는_메모를_조회하면_NotFoundException이_발생한다() {
        assertThatThrownBy(() -> memoService.getMemo(memberId, 9_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 메모를_수정하면_제목_본문과_수정일이_변경된다() throws InterruptedException {
        MemoResponse saved = memoService.create(memberId, new MemoRequest("초안", "초안 본문"));
        Thread.sleep(5);

        MemoResponse updated = memoService.update(memberId, saved.memoId(), new MemoRequest(" 최종 ", " 최종 본문 "));

        assertThat(updated.title()).isEqualTo("최종");
        assertThat(updated.content()).isEqualTo("최종 본문");
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.updatedAt()).isAfter(saved.updatedAt());
    }

    @Test
    void 메모를_삭제하면_더이상_조회되지_않는다() {
        MemoResponse saved = memoService.create(memberId, new MemoRequest("삭제할 메모", "본문"));

        memoService.delete(memberId, saved.memoId());

        assertThat(memoRepository.existsById(saved.memoId())).isFalse();
    }

    @Test
    void 존재하지_않는_메모를_수정_삭제하면_NotFoundException이_발생한다() {
        assertThatThrownBy(() -> memoService.update(memberId, 9_999L, new MemoRequest("제목", "본문")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> memoService.delete(memberId, 9_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void SQL_인젝션_문자열은_일반_검색어로_처리한다() {
        memoService.create(memberId, new MemoRequest("일반 메모", "본문"));

        assertThat(memoService.getMemos(memberId, "' OR '1'='1", 0, 10).getContent()).isEmpty();
        assertThat(memoService.getMemos(memberId, "'; DROP TABLE memo; --", 0, 10).getContent()).isEmpty();
        assertThat(memoService.getMemos(memberId, "_", 0, 10).getContent()).isEmpty();
        assertThat(memoRepository.count()).isEqualTo(1);
    }

    @Test
    void 검색_키워드의_역슬래시와_밑줄도_문자_그대로_검색한다() {
        memoService.create(memberId, new MemoRequest("경로 C:\\temp", "본문"));
        memoService.create(memberId, new MemoRequest("snake_case 규칙", "본문"));
        memoService.create(memberId, new MemoRequest("일반 메모", "본문"));

        assertThat(memoService.getMemos(memberId, "\\", 0, 10).getContent())
                .extracting(MemoListItem::title).containsExactly("경로 C:\\temp");
        assertThat(memoService.getMemos(memberId, "e_c", 0, 10).getContent())
                .extracting(MemoListItem::title).containsExactly("snake_case 규칙");
    }
}
