package io.dev.coding_test.dto;

import io.dev.coding_test.model.Memo;

import java.time.LocalDateTime;

/**
 * 메모 응답.
 *
 * @param memoId    메모 ID
 * @param title     제목
 * @param content   본문
 * @param createdAt 작성일시
 * @param updatedAt 최종 수정일시
 * @param summary   AI 요약 결과
 */
public record MemoResponse(Long memoId,
                           String title,
                           String content,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt,
                           MemoSummaryResponse summary) {

    public static MemoResponse from(Memo memo) {
        return new MemoResponse(
                memo.getMemoId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getCreatedAt(),
                memo.getUpdatedAt(),
                MemoSummaryResponse.from(memo)
        );
    }
}
