package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 共享状态缓存结构化日志和运行指标收集器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class SharedStateCacheObservability {

    /** 业务日志标识。 */
    private static final String LOG_PREFIX = "[共享状态缓存]";

    /** 全局指标命名空间。 */
    private static final String GLOBAL_NAMESPACE = "global";

    /** 全局指标策略标识。 */
    private static final String GLOBAL_POLICY = "NONE";

    /** Redis 后端名称。 */
    private static final String REDIS_BACKEND = "redis";

    /** Caffeine 后端名称。 */
    private static final String CAFFEINE_BACKEND = "caffeine";

    /** Redis 未配置原因。 */
    private static final String REDIS_NOT_CONFIGURED = "redis-not-configured";

    /** Redis 不可用原因。 */
    private static final String REDIS_UNAVAILABLE = "redis-unavailable";

    /** Redis 连接配置原因。 */
    private static final String REDIS_CONFIGURED = "configured";

    /** 半开探测事件。 */
    private static final String HALF_OPEN_EVENT = "half-open";

    /** 后端选择事件。 */
    private static final String BACKEND_SELECTION_EVENT = "backend-selection";

    /** 指标维度分隔符。 */
    private static final String DIMENSION_SEPARATOR = "|";

    /** Redis 失败事件。 */
    private static final String FAILURE_EVENT = "failure";

    /** 熔断打开事件。 */
    private static final String CIRCUIT_OPEN_EVENT = "circuit-open";

    /** 降级事件。 */
    private static final String FALLBACK_EVENT = "fallback";

    /** 一致性损失事件。 */
    private static final String CONSISTENCY_LOSS_EVENT = "consistency-loss";

    /** 熔断阈值原因。 */
    private static final String FAILURE_THRESHOLD = "failure-threshold";

    /** 半开探测到期原因。 */
    private static final String PROBE_DUE = "probe-due";

    /** 本地模式结果。 */
    private static final String LOCAL_ONLY_RESULT = "local-only";

    /** 熔断打开状态。 */
    private static final String OPEN_STATE = "open";

    /** 熔断关闭状态。 */
    private static final String CLOSED_STATE = "closed";

    /** 本地镜像失效结果。 */
    private static final String INVALIDATED_MIRROR = "invalidated";

    /** 日志对象。 */
    private final Logger logger;

    /** Redis 失败计数。 */
    private final AtomicLong redisFailures = new AtomicLong();

    /** 熔断打开计数。 */
    private final AtomicLong circuitOpens = new AtomicLong();

    /** 降级操作计数。 */
    private final AtomicLong fallbacks = new AtomicLong();

    /** 一致性损失计数。 */
    private final AtomicLong consistencyLosses = new AtomicLong();

    /** Redis 恢复计数。 */
    private final AtomicLong recoveries = new AtomicLong();

    /** 半开探测计数。 */
    private final AtomicLong halfOpens = new AtomicLong();

    /** 后端选择计数。 */
    private final AtomicLong backendSelections = new AtomicLong();

    /** 带维度指标计数器。 */
    private final Map<String, AtomicLong> dimensions = new ConcurrentHashMap<>(16);

    /**
     * 使用默认 SLF4J 日志创建可观测组件。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheObservability() {
        this(LoggerFactory.getLogger(SharedStateCacheObservability.class));
    }

    /**
     * 使用指定日志对象创建可观测组件。
     *
     * @param logger 日志对象
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheObservability(Logger logger) {
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    /**
     * 记录无 Redis 的本地模式。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void localOnly() {
        backendSelected(CAFFEINE_BACKEND, REDIS_NOT_CONFIGURED);
        logger.warn("{} backend={}, reason={}", LOG_PREFIX, CAFFEINE_BACKEND,
                REDIS_NOT_CONFIGURED);
    }

    /**
     * 记录 Redis 主后端选择。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void redisSelected() {
        backendSelected(REDIS_BACKEND, REDIS_CONFIGURED);
        logger.info("{} backend={}, reason={}", LOG_PREFIX, REDIS_BACKEND, REDIS_CONFIGURED);
    }

    /**
     * 记录无 Redis 模式下的临时状态一致性损失。
     *
     * @param key 类型化缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void localOnlyState(CacheKey<?> key) {
        consistencyLosses.incrementAndGet();
        incrementDimension(key, new MetricDimension(
                CONSISTENCY_LOSS_EVENT, CAFFEINE_BACKEND, REDIS_NOT_CONFIGURED));
        logger.warn("{} backend={}, namespace={}, policy={}, result={}, reason={}",
                LOG_PREFIX, CAFFEINE_BACKEND, key.namespace().name(),
                key.namespace().fallbackPolicy(), LOCAL_ONLY_RESULT, REDIS_NOT_CONFIGURED);
    }

    /**
     * 记录 Redis 操作失败。
     *
     * @param key 类型化缓存键
     * @param exception Redis 异常
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void redisFailure(CacheKey<?> key, RuntimeException exception) {
        redisFailures.incrementAndGet();
        incrementDimension(key, new MetricDimension(
                FAILURE_EVENT, REDIS_BACKEND, exception.getClass().getSimpleName()));
        logger.warn("{} backend={}, namespace={}, policy={}, result={}, reason={}",
                LOG_PREFIX, REDIS_BACKEND, key.namespace().name(), key.namespace().fallbackPolicy(),
                FAILURE_EVENT, exception.getClass().getSimpleName());
    }

    /**
     * 记录熔断打开。
     *
     * @param key 类型化缓存键
     * @param failures 连续失败次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void circuitOpened(CacheKey<?> key, long failures) {
        circuitOpens.incrementAndGet();
        incrementDimension(key, new MetricDimension(
                CIRCUIT_OPEN_EVENT, REDIS_BACKEND, FAILURE_THRESHOLD));
        logger.warn("{} circuit={}, namespace={}, failures={}",
                LOG_PREFIX, OPEN_STATE, key.namespace().name(), failures);
    }

    /**
     * 记录半开探测。
     *
     * @param key 类型化缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void halfOpen(CacheKey<?> key) {
        halfOpens.incrementAndGet();
        incrementDimension(key, new MetricDimension(
                HALF_OPEN_EVENT, REDIS_BACKEND, PROBE_DUE));
        logger.info("{} circuit={}, namespace={}", LOG_PREFIX, HALF_OPEN_EVENT,
                key.namespace().name());
    }

    /**
     * 记录本地降级。
     *
     * @param key 类型化缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void fallback(CacheKey<?> key) {
        fallbacks.incrementAndGet();
        incrementDimension(key, new MetricDimension(
                FALLBACK_EVENT, CAFFEINE_BACKEND, REDIS_UNAVAILABLE));
        // 临时状态本地降级会丢失跨节点一致性，需要独立计量。
        if (key.namespace().fallbackPolicy() == NamespaceFallbackPolicy.STATE_FALLBACK) {
            consistencyLosses.incrementAndGet();
        }
        logger.warn("{} backend={}, namespace={}, policy={}, result={}, reason={}",
                LOG_PREFIX, CAFFEINE_BACKEND, key.namespace().name(),
                key.namespace().fallbackPolicy(), FALLBACK_EVENT, REDIS_UNAVAILABLE);
    }

    /**
     * 记录 Redis 恢复。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void recovered() {
        recoveries.incrementAndGet();
        logger.info("{} circuit={}, backend={}, localMirror={}", LOG_PREFIX,
                CLOSED_STATE, REDIS_BACKEND, INVALIDATED_MIRROR);
    }

    /**
     * 获取指标快照。
     *
     * @return 指标快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheMetrics metrics() {
        SharedStateCacheMetricTotals totals = new SharedStateCacheMetricTotals(
                redisFailures.get(), circuitOpens.get(), fallbacks.get(),
                consistencyLosses.get(), recoveries.get(), halfOpens.get(),
                backendSelections.get());
        return new SharedStateCacheMetrics(totals, dimensionSnapshot());
    }

    /**
     * 记录共享状态后端选择指标。
     *
     * @param backend 选择的后端
     * @param reason 选择原因
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void backendSelected(String backend, String reason) {
        backendSelections.incrementAndGet();
        incrementGlobalDimension(new MetricDimension(
                BACKEND_SELECTION_EVENT, backend, reason));
    }

    /**
     * 累加不属于具体命名空间的全局指标维度。
     *
     * @param dimension 全局指标维度
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void incrementGlobalDimension(MetricDimension dimension) {
        String dimensionKey = String.join(DIMENSION_SEPARATOR, dimension.event(), GLOBAL_NAMESPACE,
                GLOBAL_POLICY, dimension.backend(), dimension.reason());
        dimensions.computeIfAbsent(dimensionKey, ignored -> new AtomicLong())
                .incrementAndGet();
    }

    /**
     * 累加结构化指标维度。
     *
     * @param key 类型化缓存键
     * @param dimension 指标维度
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void incrementDimension(CacheKey<?> key, MetricDimension dimension) {
        String dimensionKey = String.join(DIMENSION_SEPARATOR, dimension.event(),
                key.namespace().name(),
                key.namespace().fallbackPolicy().name(), dimension.backend(),
                dimension.reason());
        dimensions.computeIfAbsent(dimensionKey, ignored -> new AtomicLong())
                .incrementAndGet();
    }

    /**
     * 构造不可变指标维度快照。
     *
     * @return 维度计数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private Map<String, Long> dimensionSnapshot() {
        Map<String, Long> result = new java.util.HashMap<>(dimensions.size());
        dimensions.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    /**
     * 指标事件维度。
     *
     * @param event 指标事件
     * @param backend 执行后端
     * @param reason 事件原因
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private record MetricDimension(String event, String backend, String reason) { }
}
