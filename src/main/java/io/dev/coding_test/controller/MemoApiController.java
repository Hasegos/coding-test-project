package io.dev.coding_test.controller;

import io.dev.coding_test.common.security.LoginUserId;
import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.dto.MemoSummaryResponse;
import io.dev.coding_test.dto.MemoSummaryStatusResponse;
import io.dev.coding_test.dto.PageResponse;
import io.dev.coding_test.service.MemoService;
import io.dev.coding_test.service.MemoSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 메모 REST API를 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/memos")
public class MemoApiController {

    private final MemoService memoService;
    private final MemoSummaryService memoSummaryService;

    /**
     * 메모를 작성한다. 저장 후 로컬 LLM 요약이 비동기로 시작된다.
     *
     * @param userId  로그인한 회원 ID
     * @param request 메모 작성 요청
     * @return 201 Created, 저장된 메모
     */
    @PostMapping
    public ResponseEntity<MemoResponse> create(@LoginUserId Long userId,
                                               @Valid @RequestBody MemoRequest request) {
        MemoResponse memo = memoService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/memos/" + memo.memoId())).body(memo);
    }

    /**
     * 메모 목록을 최신순으로 조회한다.
     *
     * @param userId  로그인한 회원 ID
     * @param keyword 제목/본문 검색 키워드 (선택)
     * @param page    페이지 번호 (0부터 시작)
     * @param size    페이지 크기 (최대 50)
     * @return 메모 목록 페이지
     */
    @GetMapping
    public PageResponse<MemoListItem> list(@LoginUserId Long userId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(memoService.getMemos(userId, keyword, page, size));
    }

    /**
     * 메모 단건을 조회한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @return 메모, 없으면 404
     */
    @GetMapping("/{memoId}")
    public MemoResponse get(@LoginUserId Long userId,
                            @PathVariable Long memoId) {
        return memoService.getMemo(userId, memoId);
    }

    /**
     * 메모 제목과 본문을 수정한다.
     *
     * @param userId  로그인한 회원 ID
     * @param memoId  메모 ID
     * @param request 메모 수정 요청
     * @return 수정된 메모, 없으면 404
     */
    @PutMapping("/{memoId}")
    public MemoResponse update(@LoginUserId Long userId,
                               @PathVariable Long memoId, @Valid @RequestBody MemoRequest request) {
        return memoService.update(userId, memoId, request);
    }

    /**
     * 메모를 삭제한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @return 204 No Content, 없으면 404
     */
    @DeleteMapping("/{memoId}")
    public ResponseEntity<Void> delete(@LoginUserId Long userId,
                                       @PathVariable Long memoId) {
        memoService.delete(userId, memoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 메모의 AI 요약 결과(상태, 요약문, 할 일 목록)를 조회한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @return 요약 결과, 메모가 없으면 404
     */
    @GetMapping("/{memoId}/summary")
    public MemoSummaryResponse getSummary(@LoginUserId Long userId,
                                          @PathVariable Long memoId) {
        return memoSummaryService.getSummary(userId, memoId);
    }

    /**
     * 메모의 요약 상태만 조회한다.
     * 화면은 요약이 끝날 때까지 이 경량 API를 주기적으로 호출하고, 상태가 바뀌면 요약 패널을 다시 불러온다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @return 요약 상태, 메모가 없으면 404
     */
    @GetMapping("/{memoId}/summary/status")
    public MemoSummaryStatusResponse getSummaryStatus(@LoginUserId Long userId,
                                                      @PathVariable Long memoId) {
        return memoSummaryService.getSummaryStatus(userId, memoId);
    }

    /**
     * 메모 재요약을 요청한다. 이미 요약 중이면 현재 상태를 그대로 반환한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @return 202 Accepted, 요청 후 요약 상태
     */
    @PostMapping("/{memoId}/summary")
    public ResponseEntity<MemoSummaryResponse> retrySummary(@LoginUserId Long userId,
                                                            @PathVariable Long memoId) {
        return ResponseEntity.accepted().body(memoSummaryService.retry(userId, memoId));
    }
}
