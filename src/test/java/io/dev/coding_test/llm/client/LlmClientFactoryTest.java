package io.dev.coding_test.llm.client;

import com.sun.net.httpserver.HttpServer;
import io.dev.coding_test.llm.config.LlmProperties;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.llm.exception.LlmUnavailableException;
import io.dev.coding_test.llm.guard.LlmHostGuard;
import io.dev.coding_test.llm.parser.SummaryResultParser;
import io.dev.coding_test.llm.provider.lmstudio.LmStudioLlmClient;
import io.dev.coding_test.llm.provider.ollama.OllamaLlmClient;
import io.dev.coding_test.model.enums.LlmProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 HTTP 호출(JDK HttpClient)로 주소 재검사, provider 선택, 클라이언트 재사용, 인증 헤더,
 * 리다이렉트 차단, 연결 실패·타임아웃(전체 제한 시간) 메시지 변환을 검증한다.
 * <p>
 * 테스트 서버는 127.0.0.1에 띄우므로 주소 검사를 통과시키는 가드({@link AllowAllHostGuard})를 사용한다.
 * </p>
 */
class LlmClientFactoryTest {

    private final SummaryResultParser parser = new SummaryResultParser(JsonMapper.builder().build());
    private final LlmClientFactory factory =
            new LlmClientFactory(new LlmProperties(0.2, null, null, null, null, 1, 10, 8), parser, new AllowAllHostGuard());

    @Test
    void 실제_주소_검사로_루프백과_차단_대역은_클라이언트를_만들지_않는다() {
        LlmClientFactory guarded = new LlmClientFactory(
                new LlmProperties(0.2, null, null, null, null, 1, 10, 8), parser, new LlmHostGuard("db.internal"));

        assertThatThrownBy(() -> guarded.create(connection(LlmProvider.OLLAMA, "127.0.0.1", 11434, null)))
                .isInstanceOf(LlmException.class)
                .hasMessage(LlmHostGuard.LOCALHOST_MESSAGE);
        assertThatThrownBy(() -> guarded.getClient(connection(LlmProvider.OLLAMA, "169.254.169.254", 80, null)))
                .isInstanceOf(LlmException.class)
                .hasMessage(LlmHostGuard.BLOCKED_MESSAGE);
        assertThat(guarded.create(connection(LlmProvider.OLLAMA, "192.168.0.10", 11434, null)))
                .isInstanceOf(OllamaLlmClient.class);
    }

    @Test
    void 저장된_주소도_호출할_때마다_다시_검사한다() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        LlmClientFactory switching = new LlmClientFactory(new LlmProperties(0.2, null, null, null, null, 1, 10, 8), parser,
                new LlmHostGuard("") {
                    @Override
                    public Optional<String> rejectReason(String rawHost) {
                        return allowed.get() ? Optional.empty() : Optional.of("차단");
                    }
                });
        LlmConnection connection = connection(LlmProvider.OLLAMA, "192.168.0.10", 11434, null);
        switching.getClient(connection);

        allowed.set(false);

        assertThatThrownBy(() -> switching.getClient(connection)).isInstanceOf(LlmException.class).hasMessage("차단");
    }

    @Test
    void provider에_따라_클라이언트_구현체를_선택한다() {
        assertThat(factory.create(connection(LlmProvider.OLLAMA, 11434, null))).isInstanceOf(OllamaLlmClient.class);
        assertThat(factory.create(connection(LlmProvider.LMSTUDIO, 1234, null))).isInstanceOf(LmStudioLlmClient.class);
    }

    @Test
    void 같은_접속_정보면_클라이언트를_재사용하고_바뀌면_새로_만든다() {
        LlmClient first = factory.getClient(connection(LlmProvider.OLLAMA, 11434, null));

        assertThat(factory.getClient(connection(LlmProvider.OLLAMA, 11434, null))).isSameAs(first);
        assertThat(factory.getClient(connection(LlmProvider.OLLAMA, 11435, null))).isNotSameAs(first);
    }

    @Test
    void 회원마다_다른_접속_정보를_번갈아_써도_각각_재사용한다() {
        LlmConnection first = connection(LlmProvider.OLLAMA, "192.168.0.10", 11434, null);
        LlmConnection second = connection(LlmProvider.LMSTUDIO, "192.168.0.20", 1234, "secret");
        LlmClient firstClient = factory.getClient(first);
        LlmClient secondClient = factory.getClient(second);

        assertThat(factory.getClient(first)).isSameAs(firstClient);
        assertThat(factory.getClient(second)).isSameAs(secondClient);
    }

    @Test
    void 오래_쓰지_않은_접속_정보부터_캐시에서_뺀다() {
        LlmClient oldest = factory.getClient(connection(LlmProvider.OLLAMA, "192.168.0.10", 1, null));
        for (int port = 2; port <= LlmClientFactory.CACHE_SIZE + 1; port++) {
            factory.getClient(connection(LlmProvider.OLLAMA, "192.168.0.10", port, null));
        }

        assertThat(factory.getClient(connection(LlmProvider.OLLAMA, "192.168.0.10", 1, null))).isNotSameAs(oldest);
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
            LlmClient client = factory.create(connection(LlmProvider.LMSTUDIO, server.getAddress().getPort(), "secret"));

            assertThat(client.summarize("제목", "본문").summary()).isEqualTo("요약");
            assertThat(authorization[0]).isEqualTo("Bearer secret");
            // chunked 요청을 처리하지 못하는 서버 대비: 본문 길이를 명시해야 한다
            assertThat(contentLength[0]).isNotNull().matches("[1-9][0-9]*");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 리다이렉트를_따라가지_않는다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            exchange.getResponseHeaders().add("Location", "/moved");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/moved", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            LlmClient client = factory.create(connection(LlmProvider.OLLAMA, server.getAddress().getPort(), null));

            assertThatThrownBy(client::listModels)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM 서버가 다른 주소로 리다이렉트했어요. IP·포트를 확인해주세요.");
            assertThat(redirected).hasValue(0);
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
        LlmClient client = factory.create(connection(LlmProvider.OLLAMA, closedPort, null));

        assertThatThrownBy(() -> client.summarize("제목", "본문"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("연결할 수 없어요")
                .hasMessageContaining("127.0.0.1:" + closedPort)
                .hasMessageContaining("IP·포트를 확인해주세요");
    }

    @Test
    void Tailscale_주소에_연결할_수_없으면_공유_수락_여부를_함께_안내한다() {
        assertThat(AbstractLlmClient.connectFailureMessage(connection(LlmProvider.LMSTUDIO, "100.66.180.73", 1234, null)))
                .isEqualTo("로컬 LLM 서버(http://100.66.180.73:1234)에 연결할 수 없어요. LLM PC의 Tailscale과 LLM 서버가 켜져 있는지, "
                        + "LLM PC를 운영자에게 공유했고 운영자가 수락했는지 확인해주세요.");
        assertThat(AbstractLlmClient.connectFailureMessage(connection(LlmProvider.OLLAMA, "fd7a:115c:a1e0::1", 11434, null)))
                .contains("운영자에게 공유");
        assertThat(AbstractLlmClient.connectFailureMessage(connection(LlmProvider.OLLAMA, "192.168.0.10", 11434, null)))
                .doesNotContain("Tailscale");
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
            LlmClientFactory slowFactory = new LlmClientFactory(
                    new LlmProperties(0.2, Duration.ofSeconds(1), Duration.ofMillis(300), null, null, 1, 10, 8),
                    parser, new AllowAllHostGuard());
            LlmClient client = slowFactory.create(connection(LlmProvider.OLLAMA, server.getAddress().getPort(), null));

            assertThatThrownBy(() -> client.summarize("제목", "본문"))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("응답 시간");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 본문을_조금씩_보내며_버티는_서버도_전체_제한_시간에_끊는다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("{\"models\": [".getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < 50; i++) {
                    out.write(' ');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
                // 클라이언트가 끊으면 쓰기가 실패한다
            }
        });
        server.start();
        try {
            LlmClientFactory slowFactory = new LlmClientFactory(
                    new LlmProperties(0.2, Duration.ofSeconds(1), null, Duration.ofMillis(500), null, 1, 10, 8),
                    parser, new AllowAllHostGuard());
            LlmClient client = slowFactory.create(connection(LlmProvider.OLLAMA, server.getAddress().getPort(), null));

            long start = System.nanoTime();
            assertThatThrownBy(client::listModels)
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("응답 시간(500ms)");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        } finally {
            server.stop(0);
        }
    }

    private static LlmConnection connection(LlmProvider provider, int port, String apiKey) {
        return connection(provider, "127.0.0.1", port, apiKey);
    }

    private static LlmConnection connection(LlmProvider provider, String host, int port, String apiKey) {
        return new LlmConnection(provider, host, port, "model", apiKey);
    }

    /** 테스트 서버(127.0.0.1)에 연결하기 위해 주소 검사를 통과시키는 가드 */
    private static class AllowAllHostGuard extends LlmHostGuard {

        AllowAllHostGuard() {
            super("");
        }

        @Override
        public Optional<String> rejectReason(String rawHost) {
            return Optional.empty();
        }
    }
}
