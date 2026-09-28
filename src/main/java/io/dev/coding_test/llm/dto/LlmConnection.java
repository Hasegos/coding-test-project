package io.dev.coding_test.llm.dto;

import io.dev.coding_test.model.enums.LlmProvider;

/**
 * 사용자가 LLM 설정 화면에서 입력한 로컬 LLM 서버 접속 정보.
 *
 * @param provider LLM 런타임 (Ollama / LM Studio)
 * @param host     로컬 전용 IP 주소 (사설망 · Tailscale 대역만 허용, IPv6 가능)
 * @param port     포트
 * @param model    요약에 사용할 모델명, 연결 테스트(모델 목록 조회) 시에는 비어 있을 수 있음
 * @param apiKey   인증 토큰, 없으면 {@code null}
 */
public record LlmConnection(LlmProvider provider, String host, int port, String model, String apiKey) {

    /**
     * 호출할 LLM 서버의 기본 주소를 반환한다.
     *
     * @return {@code http://host:port}, IPv6면 {@code http://[host]:port}
     */
    public String baseUrl() {
        String authority = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return "http://" + authority + ":" + port;
    }

    /**
     * 인증 토큰이 설정되어 있는지 확인한다.
     *
     * @return 토큰이 비어 있지 않으면 {@code true}
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 로그 등에 남겨도 되도록 인증 토큰을 가린 문자열을 반환한다.
     */
    @Override
    public String toString() {
        return "LlmConnection[provider=" + provider + ", baseUrl=" + baseUrl() + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "****" : "none") + "]";
    }
}
