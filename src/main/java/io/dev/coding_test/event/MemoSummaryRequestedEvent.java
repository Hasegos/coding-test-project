package io.dev.coding_test.event;

/**
 * 메모 요약이 필요할 때 발행하는 이벤트 (저장, 내용 수정, 재요약 요청).
 *
 * @param memoId   메모 ID
 * @param revision 요약 요청 시점의 메모 revision
 */
public record MemoSummaryRequestedEvent(Long memoId, long revision) {
}
