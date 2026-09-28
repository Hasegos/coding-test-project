package io.dev.coding_test.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로컬 LLM이 메모에서 추출한 할 일 항목.
 */
@Entity
@Getter
@Setter
@Table(name = "memo_todo")
@NoArgsConstructor
public class MemoTodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long todoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_id", nullable = false)
    private Memo memo;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
