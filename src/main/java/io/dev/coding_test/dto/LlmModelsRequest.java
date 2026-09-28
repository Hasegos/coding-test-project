package io.dev.coding_test.dto;

import io.dev.coding_test.common.validation.LocalIp;
import io.dev.coding_test.model.enums.LlmProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 연결 테스트(모델 목록 조회) 요청. 저장 전 입력값으로 LLM 서버에 연결해본다.
 *
 * @param provider LLM 런타임
 * @param host     로컬 전용 IPv4 주소
 * @param port     포트
 * @param apiKey   인증 토큰, 비어 있으면 저장된 토큰 사용
 */
public record LlmModelsRequest(
        @NotNull(message = "LLM 런타임을 선택해주세요.")
        LlmProvider provider,

        @NotBlank(message = "LLM 서버 IP를 입력해주세요.")
        @LocalIp
        String host,

        @NotNull(message = "포트를 입력해주세요.")
        @Min(value = 1, message = "포트는 1~65535 사이로 입력해주세요.")
        @Max(value = 65535, message = "포트는 1~65535 사이로 입력해주세요.")
        Integer port,

        @Size(max = 200, message = "API Key는 200자 이하로 입력해주세요.")
        @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "API Key에 공백이나 사용할 수 없는 문자가 있어요.")
        String apiKey) {
}
