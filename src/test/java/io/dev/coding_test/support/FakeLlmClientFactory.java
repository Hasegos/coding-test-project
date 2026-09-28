package io.dev.coding_test.support;

import io.dev.coding_test.llm.client.LlmClient;
import io.dev.coding_test.llm.client.LlmClientFactory;
import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 LLM 클라이언트 팩토리.
 * <p>
 * 테스트 컨텍스트에서 실제 팩토리 대신 주입되며({@code @Primary}), 접속 정보와 관계없이 {@link FakeLlmClient}를 반환한다.
 * 마지막으로 요청받은 접속 정보를 기록해 설정값이 올바르게 전달됐는지 검증할 수 있다.
 * </p>
 */
@Primary
@Component
public class FakeLlmClientFactory extends LlmClientFactory {

    private final FakeLlmClient fakeLlmClient;
    private final AtomicReference<LlmConnection> lastConnection = new AtomicReference<>();

    public FakeLlmClientFactory(LlmProperties properties, SummaryResultParser parser, LlmHostGuard llmHostGuard,
                                FakeLlmClient fakeLlmClient) {
        super(properties, parser, llmHostGuard);
        this.fakeLlmClient = fakeLlmClient;
    }

    @Override
    public LlmClient getClient(LlmConnection connection) {
        lastConnection.set(connection);
        return fakeLlmClient;
    }

    @Override
    public LlmClient create(LlmConnection connection) {
        lastConnection.set(connection);
        return fakeLlmClient;
    }

    public LlmConnection lastConnection() {
        return lastConnection.get();
    }
}
