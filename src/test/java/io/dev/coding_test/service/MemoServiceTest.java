package io.dev.coding_test.service;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
