package io.dev.coding_test.dto;

import java.util.List;

/**
 * 연결 테스트 결과. 연결 실패도 오류 응답이 아니라 {@code ok = false}로 담는다.
 *
 * @param ok        연결 성공 여부
 * @param latencyMs 소요 시간 (밀리초)
 * @param models    사용할 수 있는 채팅 모델 목록, 실패했거나 서버가 모델 목록을 지원하지 않으면 {@code null}
 * @param message   안내 / 실패 메시지, 모델 목록을 정상적으로 불러왔으면 {@code null}
 */
public record LlmConnectionTestResponse(boolean ok, long latencyMs, List<String> models, String message) {

    public static final String MODELS_UNSUPPORTED_MESSAGE =
            "모델 목록을 지원하지 않는 서버예요. LLM 런타임 선택이 맞는지 확인하거나 모델명을 직접 입력해주세요.";
    public static final String MODELS_EMPTY_MESSAGE =
            "사용할 수 있는 채팅 모델이 없어요. LLM 서버에서 모델을 먼저 로드해주세요.";

    public static LlmConnectionTestResponse success(long latencyMs, List<String> models) {
        String message = models == null ? MODELS_UNSUPPORTED_MESSAGE
                : models.isEmpty() ? MODELS_EMPTY_MESSAGE
                : null;
        return new LlmConnectionTestResponse(true, latencyMs, models, message);
    }

    public static LlmConnectionTestResponse failure(long latencyMs, String message) {
        return new LlmConnectionTestResponse(false, latencyMs, null, message);
    }
}
