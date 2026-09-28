package io.dev.coding_test.dto;

import io.dev.coding_test.model.Memo;

import java.time.LocalDateTime;

/**
 * 메모 목록 항목.
 *
 * @param memoId    메모 ID
 * @param title     제목
 * @param preview   본문 미리보기 (앞부분 {@value #PREVIEW_LENGTH}자)
 * @param createdAt 작성일시
 * @param updatedAt 최종 수정일시
 */
public record MemoListItem(Long memoId,
                           String title,
                           String preview,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt) {

    public static final int PREVIEW_LENGTH = 140;

    public static MemoListItem from(Memo memo) {
        return new MemoListItem(
                memo.getMemoId(),
                memo.getTitle(),
                preview(memo.getContent()),
                memo.getCreatedAt(),
                memo.getUpdatedAt()
        );
    }

    /**
     * 줄바꿈·연속 공백을 한 칸으로 합친 뒤 앞부분만 잘라 미리보기 문자열을 만든다.
     */
    private static String preview(String content) {
        String flat = content.replaceAll("\\s+", " ").strip();
        if (flat.length() <= PREVIEW_LENGTH) {
            return flat;
        }
        return flat.substring(0, PREVIEW_LENGTH) + "…";
    }
}
