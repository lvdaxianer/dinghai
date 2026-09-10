package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 主后端与 Caffeine 降级后端的容错测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class ResilientSharedStateCacheTest {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "resilient-cache";

    /** 分布式强一致命名空间。 */
    private static final String REQUIRED_NAMESPACE = "resilient-required";

    /** 临时状态降级命名空间。 */
    private static final String STATE_NAMESPACE = "resilient-state";

    /** 普通缓存键路径。 */
    private static final CacheKeyNamespace CACHE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "cache", "value");

    /** 强一致缓存键路径。 */
    private static final CacheKeyNamespace REQUIRED_KEYS =
            new CacheKeyNamespace(REQUIRED_NAMESPACE, "state", "value");

    /** 临时状态键路径。 */
    private static final CacheKeyNamespace STATE_KEYS =
            new CacheKeyNamespace(STATE_NAMESPACE, "auth", "failure");

    /** 测试时间源。 */
    private AtomicLong now;

    /** Redis 模拟后端。 */
    private SharedStateCache redis;

    /** Caffeine 本地后端。 */
    private CaffeineSharedStateCache caffeine;

    /** 缓存键构建器。 */
    private CacheKeyBuilder keyBuilder;

    /** 普通缓存键。 */
    private CacheKey<String> cacheKey;

    /** 强一致缓存键。 */
    private CacheKey<String> requiredKey;

    /** 临时状态键。 */
    private CacheKey<String> stateKey;

    /** 容错缓存门面。 */
    private ResilientSharedStateCache cache;

    /**
     * 创建测试后端和固定时间源。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    public void setUp() {
        // 固定时间源从 1 秒刻度开始，便于精确推进熔断窗口。
        now = new AtomicLong(1_000L);
        redis = mock(SharedStateCache.class);
        caffeine = ResilientTestCaches.newCaffeineBackend();
        // 注册普通缓存、临时状态和强一致三类命名空间策略。
        keyBuilder = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE),
                NamespacePolicy.stateFallback(STATE_NAMESPACE),
                NamespacePolicy.distributedRequired(REQUIRED_NAMESPACE, false)));
        // 分别构建三类命名空间下的测试缓存键。
        cacheKey = keyBuilder.build(CACHE_KEYS, "value", String.class);
        requiredKey = keyBuilder.build(REQUIRED_KEYS, "value", String.class);
        stateKey = keyBuilder.build(STATE_KEYS, "value", String.class);
        cache = new ResilientSharedStateCache(Optional.of(redis), caffeine,
                options(circuitProperties()));
    }

    /**
     * Redis 读取成功时应同步预热 Caffeine。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldWarmCaffeineAfterSuccessfulRedisRead() {
        // 准备：Redis 返回命中值。
        when(redis.get(cacheKey)).thenReturn(Optional.of("green"));

        // 执行：通过容错门面读取。
        Assert.assertEquals(Optional.of("green"), cache.get(cacheKey));
        // 断言：请求到达 Redis 且本地镜像被预热。
        verify(redis).get(cacheKey);
        Assert.assertEquals(Optional.of("green"), caffeine.get(cacheKey));
    }

    /**
     * Redis 连续失败达到阈值后普通缓存应降级到 Caffeine。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldFallbackToCaffeineAfterRedisFailureThreshold() {
        // 准备：Redis 持续失败，本地已预热旧值。
        when(redis.get(cacheKey)).thenThrow(redisFailure());
        caffeine.put(cacheKey, "local", CacheExpiry.eternal());

        // 执行：两次读取，第二次达到熔断阈值。
        Assert.assertEquals(Optional.of("local"), cache.get(cacheKey));
        Assert.assertEquals(Optional.of("local"), cache.get(cacheKey));
        // 断言：熔断打开且两次读取均降级到 Caffeine。
        Assert.assertEquals(SharedStateCircuitState.OPEN,
                cache.health().circuitState());
        Assert.assertEquals(2L, cache.metrics().fallbackCount());
    }

    /**
     * Redis 不可用时分布式强一致命名空间不得访问 Caffeine。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRejectDistributedRequiredFallback() {
        // 准备：强一致命名空间的 Redis 读取失败。
        when(redis.get(requiredKey)).thenThrow(redisFailure());
        cache = new ResilientSharedStateCache(Optional.of(redis), caffeine,
                options(circuitProperties()));

        // 执行并断言：直接失败关闭，不进入 Caffeine。
        Assert.assertThrows(SharedStateUnavailableException.class,
                () -> cache.get(requiredKey));
        // 断言：本地后端未残留任何降级值。
        Assert.assertEquals(Optional.empty(), caffeine.get(requiredKey));
    }

    /**
     * 混合批量读取包含强一致键时不得降级到本地。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldRejectMixedBulkReadContainingDistributedRequiredKey() {
        // 准备：普通键在前，强一致键在后，Redis 批量读取失败。
        when(redis.getAll(List.of(cacheKey, requiredKey))).thenThrow(redisFailure());
        caffeine.put(cacheKey, "local-cache", CacheExpiry.eternal());
        caffeine.put(requiredKey, "local-required", CacheExpiry.eternal());

        // 断言：任何包含强一致键的批量请求均保持失败关闭。
        Assert.assertThrows(SharedStateUnavailableException.class,
                () -> cache.getAll(List.of(cacheKey, requiredKey)));
    }

    /**
     * 半开探测成功后应恢复 Redis 并删除降级期间的本地写入。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRecoverAndInvalidateDegradedEntriesAfterHalfOpenProbe() {
        // 准备：阈值调为 1，首次写入失败即熔断并降级到本地。
        SharedStateCacheProperties.CircuitProperties properties = circuitProperties();
        properties.setFailureThreshold(1);
        cache = new ResilientSharedStateCache(Optional.of(redis), caffeine,
                options(properties));
        doThrow(redisFailure())
                .when(redis).put(cacheKey, "local", CacheExpiry.eternal());
        cache.put(cacheKey, "local", CacheExpiry.eternal());
        Assert.assertEquals(Optional.of("local"), caffeine.get(cacheKey));

        // 推进时间越过熔断窗口，半开探测读取将命中 Redis。
        now.set(2_001L);
        when(redis.get(cacheKey)).thenReturn(Optional.of("remote"));

        // 执行：半开探测读取成功。
        Assert.assertEquals(Optional.of("remote"), cache.get(cacheKey));
        // 断言：熔断关闭、降级期间的本地写入被清理并重新镜像。
        Assert.assertEquals(Optional.of("remote"), caffeine.get(cacheKey));
        Assert.assertEquals(SharedStateCircuitState.CLOSED,
                cache.health().circuitState());
        Assert.assertEquals(1L, cache.metrics().recoveryCount());
    }

    /**
     * 临时状态降级应显式累计一致性损失指标。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRecordConsistencyLossForStateFallback() {
        // 准备：临时状态命名空间的 Redis 读取失败。
        when(redis.get(stateKey)).thenThrow(redisFailure());

        // 执行：降级读取返回空值。
        Assert.assertEquals(Optional.empty(), cache.get(stateKey));

        // 断言：记录一次降级和一次一致性损失，并带完整维度标签。
        Assert.assertEquals(1L, cache.metrics().fallbackCount());
        Assert.assertEquals(1L, cache.metrics().consistencyLossCount());
        Assert.assertEquals(Long.valueOf(1L), cache.metrics().dimensionCounts().get(
                "fallback|resilient-state|STATE_FALLBACK|caffeine|redis-unavailable"));
    }

    /**
     * Redis 明确返回空值时应删除陈旧本地镜像。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRemoveStaleLocalValueAfterEmptyRedisRead() {
        // 准备：本地存在陈旧镜像，Redis 明确返回空。
        caffeine.put(cacheKey, "stale", CacheExpiry.eternal());
        doReturn(Optional.empty()).when(redis).get(cacheKey);

        // 执行：读取返回空。
        Assert.assertEquals(Optional.empty(), cache.get(cacheKey));

        // 断言：陈旧本地镜像被删除。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));
    }

    /**
     * Redis 写入成功时应同步预热 Caffeine。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldWarmCaffeineAfterSuccessfulRedisWrite() {
        // 执行：通过容错门面写入。
        cache.put(cacheKey, "green", CacheExpiry.eternal());

        // 断言：Redis 收到写入且本地镜像同步预热。
        verify(redis).put(cacheKey, "green", CacheExpiry.eternal());
        Assert.assertEquals(Optional.of("green"), caffeine.get(cacheKey));
    }

    /**
     * Redis 恢复时应清理全部降级期间访问的本地键。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldInvalidateAllDegradedKeysAfterRecovery() {
        // 准备：阈值调为 1，两类命名空间各预热一份本地值。
        SharedStateCacheProperties.CircuitProperties properties = circuitProperties();
        properties.setFailureThreshold(1);
        cache = new ResilientSharedStateCache(Optional.of(redis), caffeine, options(properties));
        when(redis.get(cacheKey)).thenThrow(redisFailure());
        caffeine.put(cacheKey, "cache-local", CacheExpiry.eternal());
        caffeine.put(stateKey, "state-local", CacheExpiry.after(Duration.ofSeconds(30L)));

        // 执行：两个命名空间各发生一次降级读取。
        Assert.assertEquals(Optional.of("cache-local"), cache.get(cacheKey));
        Assert.assertEquals(Optional.of("state-local"), cache.get(stateKey));
        // 推进时间越过熔断窗口，下次读取触发半开探测。
        now.set(2_001L);
        doReturn(Optional.empty()).when(redis).get(cacheKey);

        // 执行并断言：探测成功返回空值。
        Assert.assertEquals(Optional.empty(), cache.get(cacheKey));
        // 断言：恢复后清理全部降级期间访问过的本地键。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));
        Assert.assertEquals(Optional.empty(), caffeine.get(stateKey));
    }

    /**
     * 半开状态应只允许一个并发请求探测 Redis。
     *
     * @throws Exception 并发测试执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldAllowSingleRedisProbeWhenCircuitIsHalfOpen() throws Exception {
        // 准备：统计真实到达 Redis 的调用次数，并让其持续失败。
        AtomicInteger redisCalls = new AtomicInteger();
        when(redis.get(cacheKey)).thenAnswer(ignored -> {
            redisCalls.incrementAndGet();
            throw redisFailure();
        });
        // 两次失败使熔断达到阈值并打开。
        cache.get(cacheKey);
        cache.get(cacheKey);
        // 推进时间越过熔断窗口，进入半开状态。
        now.set(2_001L);
        SharedStateCacheAtomicTestSupport support = new SharedStateCacheAtomicTestSupport(8);
        try {
            // 执行：8 个并发读取同时冲击半开熔断器。
            runConcurrentReads(support);
        } finally {
            support.close();
        }
        // 断言：半开窗口内只有一个探测请求真正到达 Redis。
        Assert.assertEquals(3, redisCalls.get());
        Assert.assertEquals(1L, cache.metrics().halfOpenCount());
    }

    /**
     * 创建固定阈值和探测窗口的测试配置。
     *
     * @return 熔断测试配置
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private SharedStateCacheProperties.CircuitProperties circuitProperties() {
        SharedStateCacheProperties.CircuitProperties properties =
                new SharedStateCacheProperties.CircuitProperties();
        // 两次失败打开熔断，1 秒后允许半开探测，预热 TTL 30 秒。
        properties.setFailureThreshold(2);
        properties.setOpenDuration(Duration.ofSeconds(1L));
        properties.setWarmTtl(Duration.ofSeconds(30L));
        return properties;
    }

    /**
     * 创建使用测试时间源的容错运行选项。
     *
     * @param properties 熔断配置
     * @return 容错运行选项
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private ResilientSharedStateCacheOptions options(
            SharedStateCacheProperties.CircuitProperties properties) {
        // 时间源绑定到测试时钟，熔断窗口随 now 推进。
        return new ResilientSharedStateCacheOptions(properties, now::get);
    }

    /**
     * 创建可计入熔断的 Redis 后端异常。
     *
     * @return Redis 后端异常
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private RedisBackendException redisFailure() {
        // 包装为后端异常，确保只被 Redis 失败处理路径捕获。
        return new RedisBackendException(new IllegalStateException("redis down"));
    }

    /**
     * 并发执行熔断半开读取。
     *
     * @param support 并发测试辅助器
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void runConcurrentReads(SharedStateCacheAtomicTestSupport support) throws Exception {
        // 所有并发任务共用同一放行闸门。
        SharedStateCacheAtomicTestSupport.Gate gate = support.newGate();
        List<Callable<Optional<String>>> tasks = support.repeatedTasks(() ->
                support.gatedCall(gate, () -> cache.get(cacheKey)));
        // 闸门统一放行，最大化半开窗口内的并发竞争。
        List<Future<Optional<String>>> results = support.submitAndRelease(gate, tasks);
        for (Future<Optional<String>> result : results) {
            // 逐个收集结果，任务异常会在此抛出。
            result.get();
        }
    }
}
