package io.dev.coding_test.dto.memo;

import io.dev.coding_test.model.enums.SummaryStatus;

import java.time.LocalDateTime;

/**
 * 메모 목록 조회 결과(JPQL 생성자 projection).
 * <p>
 * 목록에 필요한 컬럼만 조회하기 위해 본문은 앞부분({@value #CONTENT_HEAD_LENGTH}자)만,
 * 할 일은 개수만 가져온다. (엔티티·할 일 컬렉션을 로딩하지 않음)
 * </p>
 *
 * @param memoId        메모 ID
 * @param title         제목
 * @param contentHead   본문 앞부분
 * @param createdAt     작성일시
 * @param updatedAt     최종 수정일시
 * @param summaryStatus AI 요약 진행 상태
 * @param todoCount     추출된 할 일 개수
 */
public record MemoListRow(Long memoId,
                          String title,
                          String contentHead,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt,
                          SummaryStatus summaryStatus,
                          int todoCount) {

    /** 미리보기 생성을 위해 DB에서 가져오는 본문 앞부분 길이 (공백 정리 후 140자를 만들기에 충분한 길이) */
    public static final int CONTENT_HEAD_LENGTH = 300;
}
