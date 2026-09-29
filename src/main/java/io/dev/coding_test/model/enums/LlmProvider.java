package io.dev.coding_test.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지원하는 로컬 LLM 런타임.
 */
@Getter
@RequiredArgsConstructor
public enum LlmProvider {

    /** Ollama — {@code POST /api/chat} */
    OLLAMA("Ollama", 11434),

    /** LM Studio — OpenAI 호환 {@code POST /v1/chat/completions} */
    LMSTUDIO("LM Studio", 1234);

    /** 화면에 표시할 런타임 이름 */
    private final String label;

    /** 런타임 기본 포트 */
    private final int defaultPort;
}
