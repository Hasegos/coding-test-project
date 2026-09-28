package io.dev.coding_test.model;

import io.dev.coding_test.model.enums.LlmProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원이 LLM 설정 화면에서 입력한 로컬 LLM 서버 접속 정보.
 * <p>
 * 회원마다 하나씩 두며, 회원 ID를 그대로 기본키로 쓴다. ({@code @MapsId})
 * </p>
 */
@Entity
@Getter
@Setter
@Table(name = "llm_setting")
@NoArgsConstructor
public class LlmSetting {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", foreignKey = @ForeignKey(name = "fk_llm_setting_member"))
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private LlmProvider provider;

    @Column(name = "host", nullable = false, length = 45)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "api_key", length = 200)
    private String apiKey;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
