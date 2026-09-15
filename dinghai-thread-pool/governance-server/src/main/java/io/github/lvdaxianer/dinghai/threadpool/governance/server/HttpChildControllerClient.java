package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolInstanceIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 通过受保护的子控 HTTP 协议读取快照和增量指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class HttpChildControllerClient implements ChildControllerClient {
    private static final String METRICS_PATH = "/actuator/dinghai-thread-pool/metrics";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    /**
     * 创建带连接与读取超时的子控客户端。
     *
     * @param baseUrl 已注册的子控基础地址
     * @param objectMapper JSON 编解码器
     * @param timeout 连接与读取超时
     * @param apiToken 可轮换的服务访问 Token，可为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public HttpChildControllerClient(String baseUrl, ObjectMapper objectMapper, Duration timeout, String apiToken) {
        this.objectMapper = objectMapper;
        client = createClient(baseUrl, timeout, apiToken);
    }

    /**
     * 读取一页子控增量指标。
     *
     * @param cursor 已确认 cursor，可为空
     * @param limit 每页上限
     * @return 子控指标页
     * @throws Exception 网络、协议或 cursor 过期失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public MetricPage fetch(String cursor, int limit) throws Exception {
        try {
            MetricPage page = client.get().uri(builder -> buildMetricsUri(builder, cursor, limit))
                    .retrieve().body(MetricPage.class);
            return requirePage(page);
        } catch (RestClientResponseException exception) {
            throw classifyFailure(exception);
        }
    }

    /**
     * 读取子控 SQLite 持久化卷的实例世代。
     *
     * @return 新版子控的实例世代；旧版子控未提供端点时为空
     * @throws Exception 非兼容性网络或协议失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public Optional<String> fetchInstanceVersion() throws Exception {
        try {
            ThreadPoolInstanceIdentity identity = client.get().uri("/actuator/dinghai-thread-pool/identity")
                    .retrieve().body(ThreadPoolInstanceIdentity.class);
            if (identity == null || identity.version() == null || identity.version().isBlank()) {
                throw new IllegalStateException("child thread-pool instance identity is incomplete");
            }
            return Optional.of(identity.version());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new IllegalStateException("cannot read child thread-pool instance identity", exception);
        }
    }

    /**
     * 读取子控当前快照，用于 cursor 过期后的恢复。
     *
     * @return 结构化快照列表
     * @throws Exception 网络或协议失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public List<Map<String, Object>> fetchSnapshot() throws Exception {
        try {
            List<Map<String, Object>> snapshot = client.get().uri("/actuator/dinghai-thread-pool/snapshot")
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            return snapshot == null ? List.of() : List.copyOf(snapshot);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("cannot read child thread-pool snapshot", exception);
        }
    }

    /** 创建不记录秘密的受限 HTTP 客户端。 */
    private RestClient createClient(String baseUrl, Duration timeout, String apiToken) {
        SimpleClientHttpRequestFactory factory = requestFactory(timeout);
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (apiToken != null && !apiToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
        }
        return builder.build();
    }

    /** 创建同时限制连接和读取时间的 HTTP 请求工厂。 */
    private SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("child controller timeout must be positive");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    /** 构造带有 cursor 和有界页大小的指标 URI。 */
    private java.net.URI buildMetricsUri(org.springframework.web.util.UriBuilder builder, String cursor, int limit) {
        org.springframework.web.util.UriBuilder target = builder.path(METRICS_PATH).queryParam("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            target.queryParam("cursor", cursor);
        }
        return target.build();
    }

    /** 拒绝空响应，避免总控将不完整协议视为已同步。 */
    private MetricPage requirePage(MetricPage page) {
        if (page == null || page.nextCursor() == null || page.oldestCursor() == null || page.metrics() == null) {
            throw new IllegalStateException("child thread-pool metric response is incomplete");
        }
        return page;
    }

    /** 将子控 HTTP 状态映射为总控可恢复或可诊断的异常。 */
    private Exception classifyFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 409) {
            return expiredCursor(exception);
        }
        return new IllegalStateException("cannot read child thread-pool metrics", exception);
    }

    /** 读取 409 响应中的稳定 cursor 恢复信息。 */
    private CursorExpiredException expiredCursor(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode oldestCursor = response.path("oldestCursor");
            if (oldestCursor.isTextual() && !oldestCursor.asText().isBlank()) {
                return new CursorExpiredException(oldestCursor.asText());
            }
        } catch (Exception ignored) {
            // 错误响应不含稳定字段时仍保持安全的协议失败。
        }
        return new CursorExpiredException("0");
    }
}
