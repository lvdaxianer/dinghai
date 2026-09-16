package io.github.lvdaxianer.dinghai.projectiq.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.lvdaxianer.dinghai.projectiq.integration.OpenAiGatewayConfiguration;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证远程 Reranker 只接受结构化 section ID 列表。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class OpenAiCompatibleRerankerGatewayTest {
    /**
     * 合法 JSON 内容应转换为排序后的 section ID。
     *
     * @throws Exception 本地 HTTP 服务异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void readsRankedSectionIdsFromChatCompletion() throws Exception {
        try (RerankServer server = new RerankServer("{\"rankedSectionIds\":[\"s2\",\"s1\"]}")) {
            RerankerGateway gateway = new OpenAiCompatibleRerankerGateway(server.configuration(), "rerank-test",
                    new ObjectMapper());

            assertThat(gateway.rank("如何配置", List.of(candidate("s1"), candidate("s2"))))
                    .containsExactly("s2", "s1");
        }
    }

    /**
     * 不可解析内容必须降级为空结果，由 Java 保持候选边界。
     *
     * @throws Exception 本地 HTTP 服务异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void rejectsMalformedRankingResponse() throws Exception {
        try (RerankServer server = new RerankServer("不是 JSON")) {
            RerankerGateway gateway = new OpenAiCompatibleRerankerGateway(server.configuration(), "rerank-test",
                    new ObjectMapper());

            assertThat(gateway.rank("如何配置", List.of(candidate("s1")))).isEmpty();
        }
    }

    /** 创建不含正文的最小候选。 */
    private static RerankCandidate candidate(String sectionId) {
        return new RerankCandidate(sectionId, "doc", "文档", "摘要", List.of("concept"), 0, 1.0f);
    }

    /** 用本地 Chat Completions 协议模拟 Reranker。 */
    private static final class RerankServer implements AutoCloseable {
        private final HttpServer server;
        private final String content;

        /** 创建携带指定 choices content 的服务。 */
        private RerankServer(String content) throws java.io.IOException {
            this.content = content;
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/v1/chat/completions", exchange -> respond(exchange));
            this.server.start();
        }

        /** 返回指向本地服务的网关设置。 */
        private OpenAiGatewayConfiguration configuration() {
            return new OpenAiGatewayConfiguration(URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/"),
                    "test-key", Duration.ofSeconds(1));
        }

        /** 使用 OpenAI choices 结构写入内容。 */
        private void respond(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String bodyText = "{\"choices\":[{\"message\":{\"content\":\"" + escape(content) + "\"}}]}";
            byte[] body = bodyText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        /** 转义放入 JSON 字符串的内容。 */
        private String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        /** 关闭本地服务。 */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
