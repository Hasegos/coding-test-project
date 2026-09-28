package io.dev.coding_test.controller;

import io.dev.coding_test.dto.MemoListItem;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.dto.PageResponse;
import io.dev.coding_test.service.MemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 메모를 작성한다.
     *
     * @param request 메모 작성 요청
     * @return 201 Created, 저장된 메모
     */
    @PostMapping
    public ResponseEntity<MemoResponse> create(@Valid @RequestBody MemoRequest request) {
        MemoResponse memo = memoService.create(request);
        return ResponseEntity.created(URI.create("/api/memos/" + memo.memoId())).body(memo);
    }

    /**
     * 메모 목록을 최신순으로 조회한다.
     *
     * @param keyword 제목/본문 검색 키워드 (선택)
     * @param page    페이지 번호 (0부터 시작)
     * @param size    페이지 크기 (최대 50)
     * @return 메모 목록 페이지
     */
    @GetMapping
    public PageResponse<MemoListItem> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(memoService.getMemos(keyword, page, size));
    }

    /**
     * 메모 단건을 조회한다.
     *
     * @param memoId 메모 ID
     * @return 메모, 없으면 404
     */
    @GetMapping("/{memoId}")
    public MemoResponse get(@PathVariable Long memoId) {
        return memoService.getMemo(memoId);
    }
}
