package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 明确结果与 Caffeine 本地镜像的一致性测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class ResilientSharedStateCacheMirrorTest {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "resilient-mirror";

    /** 普通缓存键路径。 */
    private static final CacheKeyNamespace CACHE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "cache", "mirror");

    /** Redis 模拟后端。 */
    private SharedStateCache redis;

    /** Caffeine 本地后端。 */
    private CaffeineSharedStateCache caffeine;

    /** 普通缓存键。 */
    private CacheKey<String> cacheKey;

    /** 容错缓存门面。 */
    private ResilientSharedStateCache cache;

    /**
     * 创建镜像一致性测试后端。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    public void setUp() {
        // Redis 后端使用 mock，按用例定制返回。
        redis = mock(SharedStateCache.class);
        // 本地后端使用共享测试夹具创建。
        caffeine = ResilientTestCaches.newCaffeineBackend();
        // 普通缓存命名空间使用 cacheFallback 策略构建键。
        cacheKey = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE)))
                .build(CACHE_KEYS, "value", String.class);
        // 使用默认熔断配置和固定时间源创建门面。
        cache = new ResilientSharedStateCache(Optional.of(redis), caffeine,
                new ResilientSharedStateCacheOptions(
                        new SharedStateCacheProperties.CircuitProperties(),
                        new AtomicLong(1_000L)::get));
    }

    /**
     * Redis 否定原子写入时应删除无法确认的本地镜像。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldInvalidateLocalMirrorAfterRejectedRedisMutation() {
        // 准备：本地存在陈旧镜像，Redis 否定原子写入。
        caffeine.put(cacheKey, "stale", CacheExpiry.eternal());
        when(redis.putIfAbsent(cacheKey, "new", CacheExpiry.eternal())).thenReturn(false);

        // 执行：putIfAbsent 返回 false。
        Assert.assertFalse(cache.putIfAbsent(cacheKey, "new", CacheExpiry.eternal()));
        // 断言：无法确认的本地镜像被删除。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));

        // 准备：再次放入陈旧镜像，构造 before→after 比较更新命令。
        caffeine.put(cacheKey, "stale", CacheExpiry.eternal());
        CompareAndSetCommand<String> command =
                new CompareAndSetCommand<>("before", "after", CacheExpiry.eternal());
        // Redis 比较更新失败。
        when(redis.compareAndSet(cacheKey, command)).thenReturn(false);

        // 执行：compareAndSet 返回 false。
        Assert.assertFalse(cache.compareAndSet(cacheKey, command));
        // 断言：本地镜像同样被删除。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));
    }

    /**
     * Redis 明确键不存在时应删除本地镜像。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldInvalidateLocalMirrorWhenRedisReportsMissingKey() {
        // 准备：本地存在镜像。
        caffeine.put(cacheKey, "stale", CacheExpiry.eternal());
        // Redis 明确报告键不存在。
        when(redis.exists(cacheKey)).thenReturn(false);

        // 执行：exists 返回 false。
        Assert.assertFalse(cache.exists(cacheKey));

        // 断言：本地镜像按 Redis 结论删除。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));
    }

    /**
     * Redis 批量读取命中时应预热本地镜像。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldWarmLocalMirrorAfterSuccessfulRedisBulkRead() {
        // 准备：Redis 批量读取返回单个命中值。
        when(redis.getAll(List.of(cacheKey))).thenReturn(Map.of(cacheKey, "remote"));

        // 执行：通过容错门面批量读取。
        Assert.assertEquals(Map.of(cacheKey, "remote"), cache.getAll(List.of(cacheKey)));

        // 断言：成功结果被镜像，后续降级读取可以复用本地值。
        Assert.assertEquals(Optional.of("remote"), caffeine.get(cacheKey));
    }

    /**
     * Redis 在阈值前恢复时也应清理短暂降级数据。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldInvalidateDegradedDataWhenRedisRecoversBeforeThreshold() {
        // 准备：首次读取失败，随后 Redis 在阈值前恢复。
        when(redis.get(cacheKey))
                .thenThrow(new RedisBackendException(
                        new IllegalStateException("redis down")))
                .thenReturn(Optional.empty());
        // 本地预置降级期间写入的值。
        caffeine.put(cacheKey, "local", CacheExpiry.eternal());

        // 执行：第一次降级命中本地，第二次由恢复后的 Redis 返回空。
        Assert.assertEquals(Optional.of("local"), cache.get(cacheKey));
        Assert.assertEquals(Optional.empty(), cache.get(cacheKey));

        // 断言：短暂降级数据被清理，熔断保持关闭并记录一次恢复。
        Assert.assertEquals(Optional.empty(), caffeine.get(cacheKey));
        Assert.assertEquals(SharedStateCircuitState.CLOSED, cache.health().circuitState());
        Assert.assertEquals(1L, cache.metrics().recoveryCount());
    }

    /**
     * 非 Redis 后端异常不得增加失败次数或打开熔断。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldNotCircuitBreakCallerOrDataErrors() {
        // 准备：Redis 抛出调用方数据错误，而非后端故障。
        when(redis.get(cacheKey)).thenThrow(new IllegalArgumentException("invalid value"));

        // 执行并断言：异常原样抛给调用方。
        Assert.assertThrows(IllegalArgumentException.class, () -> cache.get(cacheKey));

        // 断言：失败计数与熔断状态不受影响。
        Assert.assertEquals(0L, cache.health().consecutiveFailures());
        Assert.assertEquals(SharedStateCircuitState.CLOSED, cache.health().circuitState());
        // 指标中的 Redis 失败次数同样保持为零。
        Assert.assertEquals(0L, cache.metrics().redisFailureCount());
    }
}
