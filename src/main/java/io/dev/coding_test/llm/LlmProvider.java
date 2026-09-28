package io.dev.coding_test.llm;

/**
 * 지원하는 로컬 LLM 런타임.
 * <p>
 * {@code llm.provider} 설정값으로 선택하며(대소문자 무시), {@code llm.base-url}을 비워두면
 * 런타임별 기본 주소를 사용한다.
 * </p>
 */
public enum LlmProvider {

    /** Ollama — {@code POST /api/chat} */
    OLLAMA("http://localhost:11434"),

    /** LM Studio — OpenAI 호환 {@code POST /v1/chat/completions} */
    LMSTUDIO("http://localhost:1234");

    private final String defaultBaseUrl;

    LlmProvider(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }
}
