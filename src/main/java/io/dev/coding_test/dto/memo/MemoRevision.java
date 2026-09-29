package io.dev.coding_test.dto.memo;

/**
 * 메모 ID·작성자 ID·revision만 담은 조회 결과. (요약 재요청 이벤트 발행용)
 *
 * @param memoId   메모 ID
 * @param userId   메모 작성자 ID
 * @param revision 메모 revision
 */
public record MemoRevision(Long memoId, Long userId, long revision) {
}
