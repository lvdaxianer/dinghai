package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 共享状态缓存 Micrometer 指标桥接器测试。
 *
 * <p>直接构造容错缓存与内存注册表驱动熔断事件，
 * 验证事件订阅递增、快照播种与熔断状态仪表读数。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class SharedStateCacheMetricsBinderTest {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "metrics-cache";

    /** 临时状态降级命名空间。 */
    private static final String STATE_NAMESPACE = "metrics-state";

    /** 普通缓存键路径。 */
    private static final CacheKeyNamespace CACHE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "cache", "value");

    /** 临时状态键路径。 */
    private static final CacheKeyNamespace STATE_KEYS =
            new CacheKeyNamespace(STATE_NAMESPACE, "auth", "session");

    /** 熔断关闭仪表读数。 */
    private static final double CLOSED_GAUGE_VALUE = 0.0;

    /** 熔断打开仪表读数。 */
    private static final double OPEN_GAUGE_VALUE = 2.0;

    /** 双精度断言误差。 */
    private static final double DOUBLE_DELTA = 0.0;

    /** 测试时间源。 */
    private AtomicLong now;

    /** Redis 模拟后端。 */
    private SharedStateCache redis;

    /** 容错缓存门面。 */
    private ResilientSharedStateCache cache;

    /** 普通缓存键。 */
    private CacheKey<String> cacheKey;

    /** 临时状态键。 */
    private CacheKey<String> stateKey;

    /** Micrometer 内存注册表。 */
    private SimpleMeterRegistry registry;

    /**
     * 创建带模拟 Redis 后端的容错缓存与内存指标注册表。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Before
    public void setUp() {
        // 固定时间源从 1 秒刻度开始，便于精确推进熔断窗口。
        now = new AtomicLong(1_000L);
        redis = mock(SharedStateCache.class);
        // 注册普通缓存与临时状态两类命名空间策略。
        CacheKeyBuilder keyBuilder = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE),
                NamespacePolicy.stateFallback(STATE_NAMESPACE)));
        // 分别构建两类命名空间下的测试缓存键。
        cacheKey = keyBuilder.build(CACHE_KEYS, "value", String.class);
        stateKey = keyBuilder.build(STATE_KEYS, "value", String.class);
        // 以模拟 Redis 主后端构造容错门面，构造即记录一次后端选择事件。
        cache = new ResilientSharedStateCache(Optional.of(redis),
                ResilientTestCaches.newCaffeineBackend(), options());
        registry = new SimpleMeterRegistry();
    }

    /**
     * 桥接器应以快照播种并订阅事件递增七类事件计数器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldSeedSnapshotAndIncrementEventCounters() {
        // 准备：缓存构造时已选择 Redis 后端，桥接器创建后应按快照播种。
        new SharedStateCacheMetricsBinder(registry, cache);
        assertSeededCounters();

        // 执行：临时状态键连续两次读取失败，熔断打开并伴随降级与一致性损失。
        when(redis.get(stateKey)).thenThrow(redisFailure());
        cache.get(stateKey);
        cache.get(stateKey);

        // 断言：失败、降级、一致性损失、熔断打开事件均按次递增。
        assertFallbackFailureCounters();
    }

    /**
     * 熔断状态仪表应实时反映健康快照中的熔断状态。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldReflectCircuitStateInGaugeAndCountRecovery() {
        // 准备：注册桥接器后熔断关闭，仪表读数应为关闭态。
        new SharedStateCacheMetricsBinder(registry, cache);
        assertCircuitGauge(CLOSED_GAUGE_VALUE);

        // 执行：两次失败后熔断打开，仪表读数应切换为打开态。
        when(redis.get(cacheKey)).thenThrow(redisFailure());
        cache.get(cacheKey);
        cache.get(cacheKey);
        assertCircuitGauge(OPEN_GAUGE_VALUE);

        // 执行：推进时间越过熔断窗口，半开探测命中恢复后的 Redis。
        now.set(2_001L);
        doReturn(Optional.of("remote")).when(redis).get(cacheKey);
        cache.get(cacheKey);

        // 断言：半开与恢复事件递增，仪表回到关闭读数。
        assertRecoveryCounters();
        assertCircuitGauge(CLOSED_GAUGE_VALUE);
    }

    /**
     * 断言桥接器创建时按指标快照完成播种。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertSeededCounters() {
        // 断言：后端选择事件按快照播种为 1。
        Assert.assertEquals(1.0, eventCount(SharedStateCacheEventListener.BACKEND_SELECTION_EVENT),
                DOUBLE_DELTA);
        // 断言：尚未发生 Redis 调用，失败事件从 0 开始。
        Assert.assertEquals(0.0, eventCount(SharedStateCacheEventListener.REDIS_FAILURE_EVENT),
                DOUBLE_DELTA);
    }

    /**
     * 断言临时状态连续两次读取失败后的事件计数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertFallbackFailureCounters() {
        // 断言：两次失败各记录一次 Redis 失败与本地降级事件。
        Assert.assertEquals(2.0, eventCount(SharedStateCacheEventListener.REDIS_FAILURE_EVENT),
                DOUBLE_DELTA);
        Assert.assertEquals(2.0, eventCount(SharedStateCacheEventListener.FALLBACK_EVENT),
                DOUBLE_DELTA);
        // 断言：临时状态降级各伴随一次一致性损失事件。
        Assert.assertEquals(2.0, eventCount(SharedStateCacheEventListener.CONSISTENCY_LOSS_EVENT),
                DOUBLE_DELTA);
        // 断言：熔断恰好打开一次，且与内部指标快照一致。
        Assert.assertEquals(1.0, eventCount(SharedStateCacheEventListener.CIRCUIT_OPEN_EVENT),
                DOUBLE_DELTA);
        Assert.assertEquals(1L, cache.metrics().circuitOpenCount());
    }

    /**
     * 断言半开探测成功后的事件计数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertRecoveryCounters() {
        // 断言：熔断窗口后首个请求记录一次半开探测事件。
        Assert.assertEquals(1.0, eventCount(SharedStateCacheEventListener.HALF_OPEN_EVENT),
                DOUBLE_DELTA);
        // 断言：探测成功记录一次 Redis 恢复事件。
        Assert.assertEquals(1.0, eventCount(SharedStateCacheEventListener.RECOVERY_EVENT),
                DOUBLE_DELTA);
    }

    /**
     * 断言熔断状态仪表当前读数。
     *
     * @param expected 期望仪表读数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertCircuitGauge(double expected) {
        // 断言：仪表直读健康快照中的熔断状态映射值。
        Assert.assertEquals(expected,
                registry.get(SharedStateCacheMetricsBinder.CIRCUIT_STATE_METRIC).gauge().value(),
                DOUBLE_DELTA);
    }

    /**
     * 读取带事件标签的计数器当前值。
     *
     * @param event 事件名称
     * @return 计数器当前值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private double eventCount(String event) {
        return registry.get(SharedStateCacheMetricsBinder.EVENTS_METRIC)
                .tag(SharedStateCacheMetricsBinder.EVENT_TAG, event).counter().count();
    }

    /**
     * 创建固定阈值和探测窗口的测试运行选项。
     *
     * @return 容错运行选项
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResilientSharedStateCacheOptions options() {
        SharedStateCacheProperties.CircuitProperties properties =
                new SharedStateCacheProperties.CircuitProperties();
        // 两次失败打开熔断，1 秒后允许半开探测，预热 TTL 30 秒。
        properties.setFailureThreshold(2);
        properties.setOpenDuration(Duration.ofSeconds(1L));
        properties.setWarmTtl(Duration.ofSeconds(30L));
        // 时间源绑定到测试时钟，熔断窗口随 now 推进。
        return new ResilientSharedStateCacheOptions(properties, now::get);
    }

    /**
     * 创建可计入熔断的 Redis 后端异常。
     *
     * @return Redis 后端异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private RedisBackendException redisFailure() {
        // 包装为后端异常，确保只被 Redis 失败处理路径捕获。
        return new RedisBackendException(new IllegalStateException("redis down"));
    }
}
