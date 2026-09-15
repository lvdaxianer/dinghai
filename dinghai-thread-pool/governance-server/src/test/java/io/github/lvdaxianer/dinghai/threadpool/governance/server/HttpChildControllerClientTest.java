package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** 验证总控对子控指标协议的 HTTP 读取与错误分类。 */
public class HttpChildControllerClientTest {

    /** 正常指标页必须携带 cursor 和受限 page 参数。 */
    @Test
    public void shouldFetchMetricPage() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        try (TestServer server = server(exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, "{\"nextCursor\":\"2\",\"hasMore\":false,\"oldestCursor\":\"1\",\"metrics\":[]}");
        })) {
            HttpChildControllerClient client = client(server.baseUrl());

            assertEquals("2", client.fetch("1", 100).nextCursor());
            assertEquals(true, query.get().contains("cursor=1"));
            assertEquals(true, query.get().contains("limit=100"));
        }
    }

    /** 子控 409 协议错误必须转换为可恢复的 cursor 过期异常。 */
    @Test
    public void shouldClassifyExpiredCursor() throws Exception {
        try (TestServer server = server(exchange ->
                respond(exchange, 409, "{\"code\":\"CURSOR_EXPIRED\",\"oldestCursor\":\"9\"}"))) {
            CursorExpiredException exception = assertThrows(CursorExpiredException.class,
                    () -> client(server.baseUrl()).fetch("1", 100));

            assertEquals("9", exception.oldestCursor());
        }
    }

    private HttpChildControllerClient client(String baseUrl) {
        return new HttpChildControllerClient(baseUrl, new ObjectMapper(), Duration.ofSeconds(1), "");
    }

    private TestServer server(ExchangeHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/actuator/dinghai-thread-pool/metrics", handler::handle);
        server.start();
        return new TestServer(server);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record TestServer(HttpServer server) implements AutoCloseable {
        private String baseUrl() { return "http://localhost:" + server.getAddress().getPort(); }

        @Override
        public void close() { server.stop(0); }
    }
}
