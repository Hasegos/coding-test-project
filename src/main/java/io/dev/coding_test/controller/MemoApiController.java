package io.dev.coding_test.controller;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.service.MemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
