package io.dev.coding_test.service;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.model.Memo;
import io.dev.coding_test.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메모 작성·조회·수정·삭제 비즈니스 로직을 처리하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoService {

    private final MemoRepository memoRepository;

    /**
     * 메모를 저장한다. 제목/본문의 앞뒤 공백은 제거한다.
     *
     * @param request 메모 작성 요청
     * @return 저장된 메모
     */
    @Transactional
    public MemoResponse create(MemoRequest request) {
        Memo memo = memoRepository.save(new Memo(request.getTitle().strip(), request.getContent().strip()));
        log.info("메모 저장 - memoId: {}", memo.getMemoId());
        return MemoResponse.from(memo);
    }
}
