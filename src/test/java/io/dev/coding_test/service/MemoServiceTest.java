package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
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
    private MemoService memoService;

    @Autowired
    private MemoRepository memoRepository;

    @Test
    void 메모를_저장하면_앞뒤_공백을_제거하고_작성일을_기록한다() {
        MemoResponse saved = memoService.create(new MemoRequest("  주간 회의  ", "\n배포 일정 논의\n"));

        Memo memo = memoRepository.findById(saved.memoId()).orElseThrow();
        assertThat(memo.getTitle()).isEqualTo("주간 회의");
        assertThat(memo.getContent()).isEqualTo("배포 일정 논의");
        assertThat(memo.getCreatedAt()).isNotNull();
        assertThat(memo.getUpdatedAt()).isEqualTo(memo.getCreatedAt());
    }

    @Test
    void 메모_목록은_최신순으로_조회된다() {
        memoService.create(new MemoRequest("첫 번째", "본문"));
        memoService.create(new MemoRequest("두 번째", "본문"));

        Page<MemoListItem> page = memoService.getMemos(null, 0, 10);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(MemoListItem::title).containsExactly("두 번째", "첫 번째");
    }

    @Test
    void 키워드로_제목과_본문을_대소문자_구분없이_검색한다() {
        memoService.create(new MemoRequest("Spring 회의", "JPA 논의"));
        memoService.create(new MemoRequest("장보기", "우유, spring water"));
        memoService.create(new MemoRequest("운동", "스쿼트"));

        Page<MemoListItem> page = memoService.getMemos("  SPRING ", 0, 10);

        assertThat(page.getContent()).extracting(MemoListItem::title)
                .containsExactlyInAnyOrder("Spring 회의", "장보기");
    }

    @Test
    void 검색_키워드의_퍼센트_문자는_와일드카드로_해석하지_않는다() {
        memoService.create(new MemoRequest("달성률 100%", "본문"));
        memoService.create(new MemoRequest("일반 메모", "본문"));

        assertThat(memoService.getMemos("%", 0, 10).getContent())
                .extracting(MemoListItem::title).containsExactly("달성률 100%");
    }

    @Test
    void 페이지_번호와_크기는_허용_범위로_보정된다() {
        Page<MemoListItem> page = memoService.getMemos(null, -3, 1_000);

        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(MemoService.MAX_PAGE_SIZE);
    }

    @Test
    void 목록_미리보기는_공백을_합치고_길이를_제한한다() {
        memoService.create(new MemoRequest("긴 메모", "첫 줄\n\n   둘째 줄 " + "가".repeat(200)));

        String preview = memoService.getMemos(null, 0, 10).getContent().getFirst().preview();

        assertThat(preview).startsWith("첫 줄 둘째 줄 ");
        assertThat(preview).hasSize(MemoListItem.PREVIEW_LENGTH + 1).endsWith("…");
    }

    @Test
    void 존재하지_않는_메모를_조회하면_NotFoundException이_발생한다() {
        assertThatThrownBy(() -> memoService.getMemo(9_999L))
                .isInstanceOf(NotFoundException.class);
    }
}
