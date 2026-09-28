package io.dev.coding_test.dto;

import io.dev.coding_test.common.validation.LocalIp;
import io.dev.coding_test.model.enums.LlmProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * LLM 서버 접속 설정 저장 요청.
 * <p>
 * REST API 요청 본문과 설정 화면 폼 바인딩에 함께 사용한다.
 * {@code apiKey}를 비워두면 기존에 저장한 토큰을 유지하고, {@code clearApiKey}가 참이면 토큰을 삭제한다.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmSettingRequest {

    @NotNull(message = "LLM 런타임을 선택해주세요.")
    private LlmProvider provider;

    @NotBlank(message = "LLM 서버 IP를 입력해주세요.")
    @LocalIp
    private String host;

    @NotNull(message = "포트를 입력해주세요.")
    @Min(value = 1, message = "포트는 1~65535 사이로 입력해주세요.")
    @Max(value = 65535, message = "포트는 1~65535 사이로 입력해주세요.")
    private Integer port;

    @NotBlank(message = "모델을 선택하거나 입력해주세요.")
    @Size(max = 100, message = "모델명은 100자 이하로 입력해주세요.")
    @Pattern(regexp = "^[\\w.:@/+-]*$", message = "모델명에 사용할 수 없는 문자가 있어요.")
    private String model;

    @Size(max = 200, message = "API Key는 200자 이하로 입력해주세요.")
    @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "API Key에 공백이나 사용할 수 없는 문자가 있어요.")
    private String apiKey;

    private Boolean clearApiKey;
}
