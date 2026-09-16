package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 Qdrant REST API 的向量索引适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class QdrantVectorIndex implements VectorIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(QdrantVectorIndex.class);
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI collectionUri;
    private final Duration timeout;

    /** 创建带整体请求超时的 Qdrant 适配器。 */
    public QdrantVectorIndex(URI baseUri, String collection, ObjectMapper objectMapper, Duration timeout) {
        Objects.requireNonNull(baseUri, "baseUri 不能为空");
        Objects.requireNonNull(collection, "collection 不能为空");
        Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        Objects.requireNonNull(timeout, "timeout 不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Qdrant 超时必须为正数");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = objectMapper;
        this.collectionUri = baseUri.resolve("collections/" + collection + "/");
        this.timeout = timeout;
    }

    /** 批量写入 Qdrant point。 */
    @Override
    public void upsert(List<VectorPoint> points) throws IOException, InterruptedException {
        List<Map<String, Object>> payload = points.stream().map(this::pointPayload).toList();
        send("PUT", "/points?wait=true", Map.of("points", payload));
    }

    /** 按 sourceKey 删除 Qdrant point。 */
    @Override
    public void deleteBySourceKey(String sourceKey) throws IOException, InterruptedException {
        Map<String, Object> filter = Map.of("must", List.of(Map.of("key", "sourceKey",
                "match", Map.of("value", sourceKey))));
        send("POST", "/points/delete?wait=true", Map.of("filter", filter));
    }

    /** 查询 Qdrant 相似 point 并映射为候选记录。 */
    @Override
    public List<VectorMatch> search(List<Float> query, int limit) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/points/search", Map.of("vector", query,
                "limit", Math.max(0, limit), "with_payload", true));
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() { });
        List<Map<String, Object>> result = objectMapper.convertValue(body.get("result"), new TypeReference<>() { });
        return result.stream().map(this::match).toList();
    }

    /** 创建或重建 collection 的向量空间。 */
    @Override
    public void recreate(int dimensions) throws IOException, InterruptedException {
        send("PUT", "", Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")));
    }

    private Map<String, Object> pointPayload(VectorPoint point) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sectionId", point.sectionId());
        payload.put("documentId", point.documentId());
        payload.put("sourceKey", point.sourceKey());
        payload.put("sourceHash", point.sourceHash());
        payload.put("text", point.text());
        return Map.of("id", UUID.nameUUIDFromBytes(point.sectionId().getBytes()).toString(),
                "vector", point.vector(), "payload", payload);
    }

    private VectorMatch match(Map<String, Object> value) {
        Map<String, Object> payload = objectMapper.convertValue(value.get("payload"), new TypeReference<>() { });
        return new VectorMatch((String) payload.get("sectionId"), (String) payload.get("documentId"),
                (String) payload.get("sourceKey"), (String) payload.get("sourceHash"),
                ((Number) value.get("score")).floatValue());
    }

    private HttpResponse<String> send(String method, String path, Object body)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        URI uri = collectionUri.resolve(path.startsWith("/") ? "." + path : path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            LOGGER.debug("[Qdrant] method={} uri={} status={} durationMs={}", method, uri, response.statusCode(), elapsed);
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Qdrant 请求失败，状态码=" + response.statusCode());
            }
            return response;
        } catch (IOException | InterruptedException exception) {
            LOGGER.warn("[Qdrant] method={} uri={} 调用失败", method, uri, exception);
            throw exception;
        }
    }
}
