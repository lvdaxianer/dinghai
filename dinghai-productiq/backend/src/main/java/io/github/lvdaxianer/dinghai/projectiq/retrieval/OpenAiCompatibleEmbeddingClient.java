package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lvdaxianer.dinghai.projectiq.integration.OpenAiGatewayConfiguration;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过 OpenAI 兼容 embeddings API 生成并校验问题向量。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);
    private final OpenAiGatewayConfiguration configuration;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    /**
     * 创建受连接与读取时限约束的 Embedding 客户端。
     */
    public OpenAiCompatibleEmbeddingClient(OpenAiGatewayConfiguration configuration, String model, int dimensions,
                                           ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.model = requireModel(model);
        this.dimensions = requireDimensions(dimensions);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.client = HttpClient.newBuilder().connectTimeout(configuration.timeout()).build();
    }

    /**
     * 调用远程网关并返回维度严格匹配的向量。
     */
    @Override
    public List<Float> embed(String text) {
        try {
            return parse(execute(request(text)));
        } catch (IOException exception) {
            throw new IllegalStateException("Embedding 网关调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding 网关调用被中断", exception);
        }
    }

    /**
     * 构建只含模型和输入文本的 embeddings 请求。
     */
    private HttpRequest request(String text) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("input", Objects.requireNonNull(text, "text"));
        return HttpRequest.newBuilder(configuration.endpoint("embeddings")).timeout(configuration.timeout())
                .header("Authorization", "Bearer " + configuration.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
    }

    /**
     * 在不记录输入、响应正文或密钥的前提下调用远程网关。
     *
     * @param request 已校验的 HTTP 请求
     * @return HTTP 响应
     * @throws IOException 网络或协议调用失败
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> execute(HttpRequest request) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.debug("Embedding 网关完成, endpoint={}, status={}, durationMs={}", request.uri().getPath(),
                    response.statusCode(), durationMillis(startedAt));
            return response;
        } catch (IOException | InterruptedException exception) {
            LOGGER.warn("Embedding 网关失败, endpoint={}, durationMs={}, error={}", request.uri().getPath(),
                    durationMillis(startedAt), exception.getClass().getSimpleName());
            throw exception;
        }
    }

    /**
     * 将单调时钟起点转换为毫秒耗时。
     *
     * @param startedAt 调用起始纳秒
     * @return 已耗费毫秒
     */
    private static long durationMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * 从 OpenAI data[0].embedding 响应中读取浮点向量。
     */
    private List<Float> parse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Embedding 网关返回 HTTP " + response.statusCode());
        }
        JsonNode values = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
        if (!values.isArray() || values.size() != dimensions) {
            throw new IllegalStateException("Embedding 向量维度不匹配");
        }
        return values(values);
    }

    /**
     * 验证并冻结响应中的每一个有限浮点数。
     */
    private List<Float> values(JsonNode source) {
        List<Float> result = new ArrayList<>(dimensions);
        for (JsonNode value : source) {
            float number = value.floatValue();
            if (!value.isNumber() || !Float.isFinite(number)) {
                throw new IllegalStateException("Embedding 响应包含非法向量值");
            }
            result.add(number);
        }
        return List.copyOf(result);
    }

    /**
     * 验证模型标识。
     */
    private static String requireModel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Embedding 模型不能为空");
        }
        return value;
    }

    /**
     * 验证期望向量维度。
     */
    private static int requireDimensions(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Embedding 维度必须为正数");
        }
        return value;
    }
}
