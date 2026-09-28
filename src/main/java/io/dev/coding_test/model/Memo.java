package io.dev.coding_test.model;

import io.dev.coding_test.model.enums.SummaryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 작성한 메모 엔티티.
 * <p>
 * 로컬 LLM이 생성한 요약({@code summary})과 할 일 목록({@code todos})을 함께 가진다.
 * {@code revision}은 제목/본문이 바뀔 때마다 증가하며, 비동기 요약 결과가
 * 요청 시점의 내용과 일치할 때만 반영되도록 하는 기준으로 쓰인다.
 * 값 변경 규칙은 {@code MemoService}, {@code MemoSummaryService}가 담당한다.
 * </p>
 */
@Entity
@Getter
@Setter
@Table(name = "memo")
@NoArgsConstructor
public class Memo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long memoId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "revision", nullable = false)
    private long revision = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 20)
    private SummaryStatus summaryStatus = SummaryStatus.PENDING;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summary_error", length = 500)
    private String summaryError;

    @Column(name = "summary_model", length = 100)
    private String summaryModel;

    @Column(name = "summarized_at")
    private LocalDateTime summarizedAt;

    @OneToMany(mappedBy = "memo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<MemoTodo> todos = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
