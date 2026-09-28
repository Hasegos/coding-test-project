package io.dev.coding_test.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 작성한 메모 엔티티.
 * <p>
 * 로컬 LLM이 생성한 요약({@code summary})과 할 일 목록({@code todos})을 함께 가진다.
 * {@code revision}은 제목/본문이 바뀔 때마다 증가하며, 비동기 요약 결과가
 * 요청 시점의 내용과 일치할 때만 반영되도록 하는 기준으로 쓰인다.
 * {@code updatedAt}은 사용자가 제목/본문을 수정했을 때만 갱신한다(요약 결과 반영은 수정으로 보지 않는다).
 * </p>
 */
@Entity
@Getter
@Table(name = "memo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public Memo(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 메모 제목과 본문을 수정한다.
     * <p>
     * 내용이 실제로 바뀐 경우에만 {@code revision}을 올리고 기존 요약을 비운 뒤 요약 대기 상태로 되돌린다.
     * </p>
     *
     * @param title   수정할 제목
     * @param content 수정할 본문
     * @return 제목 또는 본문이 바뀌었으면 {@code true}
     */
    public boolean update(String title, String content) {
        if (this.title.equals(title) && this.content.equals(content)) {
            return false;
        }
        this.title = title;
        this.content = content;
        this.updatedAt = now();
        this.revision++;
        this.summary = null;
        this.summaryModel = null;
        this.summarizedAt = null;
        this.todos.clear();
        markSummaryPending();
        return true;
    }

    /**
     * 요약 대기 상태로 변경한다. (재요약 요청)
     */
    public void markSummaryPending() {
        this.summaryStatus = SummaryStatus.PENDING;
        this.summaryError = null;
    }

    /**
     * 요약 진행 중 상태로 변경한다.
     */
    public void markSummaryProcessing() {
        this.summaryStatus = SummaryStatus.PROCESSING;
    }

    /**
     * 요약 결과를 반영하고 완료 상태로 변경한다. 기존 할 일 목록은 새 목록으로 교체한다.
     *
     * @param summary 요약문
     * @param todos   할 일 목록 (순서 유지)
     * @param model   요약에 사용한 LLM 모델명
     */
    public void completeSummary(String summary, List<String> todos, String model) {
        this.summary = summary;
        this.summaryModel = model;
        this.summaryError = null;
        this.summarizedAt = now();
        this.summaryStatus = SummaryStatus.DONE;
        this.todos.clear();
        for (int i = 0; i < todos.size(); i++) {
            this.todos.add(new MemoTodo(this, todos.get(i), i));
        }
    }

    /**
     * 요약 실패 상태로 변경한다. 이전에 성공한 요약이 있으면 그대로 유지한다.
     *
     * @param error 사용자에게 보여줄 실패 사유
     */
    public void failSummary(String error) {
        this.summaryStatus = SummaryStatus.FAILED;
        this.summaryError = error;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * DB(TIMESTAMP(6)) 정밀도에 맞춰 마이크로초 단위로 자른 현재 시각.
     * 저장 직후 응답과 재조회 결과의 시각이 달라지지 않도록 한다.
     */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
