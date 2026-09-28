package io.dev.coding_test.model;

import io.dev.coding_test.model.enums.LlmProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자가 LLM 설정 화면에서 입력한 로컬 LLM 서버 접속 정보.
 * <p>
 * 설정은 하나만 존재하므로 항상 고정 ID({@code 1})로 저장한다.
 * </p>
 */
@Entity
@Getter
@Setter
@Table(name = "llm_setting")
@NoArgsConstructor
public class LlmSetting {

    /** 단일 설정 행의 고정 ID */
    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "setting_id")
    private Long settingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private LlmProvider provider;

    @Column(name = "host", nullable = false, length = 15)
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
