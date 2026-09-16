package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.lvdaxianer.dinghai.projectiq.integration.OpenAiGatewayConfiguration;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证 OpenAI 兼容 Embedding 适配器只接受完整、匹配维度的向量响应。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class OpenAiCompatibleEmbeddingClientTest {
    /**
     * 正常响应应被转换为当前模型的 float 向量。
     *
     * @throws Exception 本地 HTTP 服务异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void embedsTextFromOpenAiCompatibleEndpoint() throws Exception {
        try (GatewayServer server = new GatewayServer("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}")) {
            OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(server.configuration(),
                    "embedding-test", 3, new ObjectMapper());

            assertThat(client.embed("邀请链接过期")).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(server.lastAuthorization()).isEqualTo("Bearer test-key");
        }
    }

    /**
     * 维度不匹配时不得让错误向量进入 Qdrant。
     *
     * @throws Exception 本地 HTTP 服务异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void rejectsEmbeddingWithUnexpectedDimensions() throws Exception {
        try (GatewayServer server = new GatewayServer("{\"data\":[{\"embedding\":[0.1]}]}")) {
            OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(server.configuration(),
                    "embedding-test", 3, new ObjectMapper());

            assertThatThrownBy(() -> client.embed("邀请链接过期"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("维度");
        }
    }

    /** 用本地 HTTP 服务模拟受控的 OpenAI Embedding 响应。 */
    private static final class GatewayServer implements AutoCloseable {
        private final HttpServer server;
        private final String response;
        private String authorization;

        /** 创建固定响应的网关模拟服务。 */
        private GatewayServer(String response) throws java.io.IOException {
            this.response = response;
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/v1/embeddings", exchange -> respond(exchange));
            this.server.start();
        }

        /** 返回访问此本地服务的网关配置。 */
        private OpenAiGatewayConfiguration configuration() {
            return new OpenAiGatewayConfiguration(URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/"),
                    "test-key", Duration.ofSeconds(1));
        }

        /** 读取鉴权头并输出固定 JSON。 */
        private void respond(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        /** 返回最近一次 Authorization 头。 */
        private String lastAuthorization() {
            return authorization;
        }

        /** 关闭本地模拟服务。 */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
