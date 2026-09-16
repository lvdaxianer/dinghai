package io.github.lvdaxianer.dinghai.projectiq.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * 以 JSON section ID 列表驱动的 OpenAI 兼容重排网关。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class OpenAiCompatibleRerankerGateway implements RerankerGateway {
    private static final String INSTRUCTION = "仅返回 JSON：{\"rankedSectionIds\":[\"section_id\"]}。"
            + "只能从给定候选中选择，不要解释。";
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleRerankerGateway.class);
    private final OpenAiGatewayConfiguration configuration;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    /**
     * 创建远程重排客户端。
     */
    public OpenAiCompatibleRerankerGateway(OpenAiGatewayConfiguration configuration, String model,
                                           ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.model = requireModel(model);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.client = HttpClient.newBuilder().connectTimeout(configuration.timeout()).build();
    }

    /**
     * 请求排序；任何网关或结构异常均返回空 ID 列表。
     */
    @Override
    public List<String> rank(String question, List<RerankCandidate> candidates) {
        try {
            return parse(execute(request(question, candidates)));
        } catch (IOException exception) {
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * 构建不含章节正文的结构化重排请求。
     */
    private HttpRequest request(String question, List<RerankCandidate> candidates) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", INSTRUCTION);
        messages.addObject().put("role", "user").put("content", input(question, candidates));
        return HttpRequest.newBuilder(configuration.endpoint("chat/completions")).timeout(configuration.timeout())
                .header("Authorization", "Bearer " + configuration.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
    }

    /**
     * 在不记录问题、候选正文或密钥的前提下调用远程网关。
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
            LOGGER.debug("Reranker 网关完成, endpoint={}, status={}, durationMs={}", request.uri().getPath(),
                    response.statusCode(), durationMillis(startedAt));
            return response;
        } catch (IOException | InterruptedException exception) {
            LOGGER.warn("Reranker 网关失败, endpoint={}, durationMs={}, error={}", request.uri().getPath(),
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
     * 将问题和候选元数据序列化为紧凑 JSON 文本。
     */
    private String input(String question, List<RerankCandidate> candidates) throws IOException {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("question", Objects.requireNonNull(question, "question"));
        input.set("candidates", objectMapper.valueToTree(List.copyOf(candidates)));
        return objectMapper.writeValueAsString(input);
    }

    /**
     * 解析 choices[0].message.content 中的 rankedSectionIds。
     */
    private List<String> parse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() / 100 != 2) {
            return List.of();
        }
        JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0)
                .path("message").path("content");
        return ids(content);
    }

    /**
     * 只接受 JSON object 的字符串数组字段。
     */
    private List<String> ids(JsonNode content) {
        if (!content.isTextual()) {
            return List.of();
        }
        try {
            JsonNode values = objectMapper.readTree(content.asText()).path("rankedSectionIds");
            return stringValues(values);
        } catch (IOException exception) {
            return List.of();
        }
    }

    /**
     * 冻结非空字符串 ID，保留 Java 后续白名单校验责任。
     */
    private List<String> stringValues(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        }
        return List.copyOf(result);
    }

    /**
     * 验证远程重排模型标识。
     */
    private static String requireModel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rerank 模型不能为空");
        }
        return value;
    }
}
