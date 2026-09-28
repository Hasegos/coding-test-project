package io.dev.coding_test.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 HTTP 호출(JDK HttpClient)로 provider 선택, 인증 헤더, 연결 실패·타임아웃 메시지 변환을 검증한다.
 */
class LlmConfigTest {

    private final SummaryResultParser parser = new SummaryResultParser(JsonMapper.builder().build());

    @Test
    void provider_설정에_따라_클라이언트_구현체를_선택한다() {
        assertThat(new LlmConfig().llmClient(properties(LlmProvider.OLLAMA, "http://localhost:1", null), parser))
                .isInstanceOf(OllamaLlmClient.class);
        assertThat(new LlmConfig().llmClient(properties(LlmProvider.LMSTUDIO, "http://localhost:1", null), parser))
                .isInstanceOf(LmStudioLlmClient.class);
    }

    @Test
    void api_key가_있으면_Bearer_헤더와_Content_Length를_보내고_실제_HTTP로_요약한다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String[] authorization = new String[1];
        String[] contentLength = new String[1];
        server.createContext("/v1/chat/completions", exchange -> {
            authorization[0] = exchange.getRequestHeaders().getFirst("Authorization");
            contentLength[0] = exchange.getRequestHeaders().getFirst("Content-Length");
            byte[] body = """
                    {"choices": [{"message": {"role": "assistant", "content": "{\\"summary\\": \\"요약\\", \\"todos\\": []}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            LlmClient client = new LlmConfig().llmClient(properties(LlmProvider.LMSTUDIO, baseUrl, "secret"), parser);

            assertThat(client.summarize("제목", "본문").summary()).isEqualTo("요약");
            assertThat(authorization[0]).isEqualTo("Bearer secret");
            // chunked 요청을 처리하지 못하는 서버 대비: 본문 길이를 명시해야 한다
            assertThat(contentLength[0]).isNotNull().matches("[1-9][0-9]*");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 서버에_연결할_수_없으면_연결_실패_메시지로_변환한다() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        LlmClient client = new LlmConfig().llmClient(
                properties(LlmProvider.OLLAMA, "http://127.0.0.1:" + closedPort, null), parser);

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("연결할 수 없어요")
                .hasMessageContaining("127.0.0.1:" + closedPort);
    }

    @Test
    void 응답이_늦으면_타임아웃_메시지로_변환한다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties(LlmProvider.OLLAMA,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "model", null, 0.2,
                    Duration.ofSeconds(1), Duration.ofMillis(300), 1, 10);
            LlmClient client = new LlmConfig().llmClient(properties, parser);

            assertThatThrownBy(() -> client.summarize("제목", "본문"))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("응답 시간");
        } finally {
            server.stop(0);
        }
    }

    private static LlmProperties properties(LlmProvider provider, String baseUrl, String apiKey) {
        return new LlmProperties(provider, baseUrl, "model", apiKey, 0.2, null, null, 1, 10);
    }
}
