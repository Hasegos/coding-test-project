package io.dev.coding_test.event;

/**
 * 메모 요약이 필요할 때 발행하는 이벤트 (저장, 내용 수정, 재요약 요청).
 *
 * @param memoId   메모 ID
 * @param userId   메모 작성자 ID (작성자의 LLM 서버 대기열에 넣는다)
 * @param revision 요약 요청 시점의 메모 revision
 */
public record MemoSummaryRequestedEvent(Long memoId, Long userId, long revision) {
}
