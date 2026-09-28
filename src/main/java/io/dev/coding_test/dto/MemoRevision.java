package io.dev.coding_test.dto;

/**
 * 메모 ID와 revision만 담은 조회 결과. (요약 재요청 이벤트 발행용)
 *
 * @param memoId   메모 ID
 * @param revision 메모 revision
 */
public record MemoRevision(Long memoId, long revision) {
}
