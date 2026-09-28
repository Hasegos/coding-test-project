package io.dev.coding_test.controller;

import io.dev.coding_test.common.exception.NotFoundException;
import io.dev.coding_test.dto.LlmModelsRequest;
import io.dev.coding_test.dto.LlmModelsResponse;
import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.dto.LlmSettingResponse;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.service.MemoSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 LLM 서버 접속 설정 REST API를 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settings/llm")
public class SettingApiController {

    private final LlmSettingService llmSettingService;
    private final MemoSummaryService memoSummaryService;

    /**
     * 저장된 접속 설정을 조회한다. 인증 토큰 값은 포함하지 않는다.
     *
     * @return 저장된 설정, 아직 설정하지 않았으면 404
     */
    @GetMapping
    public LlmSettingResponse get() {
        return llmSettingService.getSetting()
                .orElseThrow(() -> new NotFoundException("LLM 서버가 아직 설정되지 않았어요."));
    }

    /**
     * 접속 설정을 저장하고, 요약에 실패했던 메모를 다시 요약 요청한다.
     *
     * @param request 접속 설정 저장 요청
     * @return 저장된 설정
     */
    @PutMapping
    public LlmSettingResponse save(@Valid @RequestBody LlmSettingRequest request) {
        LlmSettingResponse saved = llmSettingService.save(request);
        memoSummaryService.retryFailed();
        return saved;
    }

    /**
     * 입력한 접속 정보로 LLM 서버에 연결해 사용 가능한 모델 목록을 조회한다. (연결 테스트)
     *
     * @param request 연결 테스트 요청
     * @return 모델명 목록, 연결 실패 시 502
     */
    @PostMapping("/models")
    public LlmModelsResponse models(@Valid @RequestBody LlmModelsRequest request) {
        return new LlmModelsResponse(llmSettingService.listModels(request));
    }
}
