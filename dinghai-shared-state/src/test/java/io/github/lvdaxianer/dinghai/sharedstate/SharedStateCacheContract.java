package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 共享状态后端通用契约测试。
 *
 * <p>后续 Redis 后端应复用该测试，以验证操作结果一致。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public abstract class SharedStateCacheContract {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "contract-cache";

    /** 临时状态命名空间。 */
    private static final String STATE_NAMESPACE = "contract-state";

    /** 普通字符串缓存键路径。 */
    private static final CacheKeyNamespace CACHE_VALUE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "contract", "value");

    /** 普通计数器缓存键路径。 */
    private static final CacheKeyNamespace CACHE_COUNTER_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "contract", "counter");

    /** 可变对象缓存键路径。 */
    private static final CacheKeyNamespace CACHE_MUTABLE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "contract", "mutable");

    /** 临时重放状态缓存键路径。 */
    private static final CacheKeyNamespace STATE_REPLAY_KEYS =
            new CacheKeyNamespace(STATE_NAMESPACE, "auth", "replay");

    /** 永久有效策略。 */
    private static final CacheExpiry ETERNAL = CacheExpiry.eternal();

    /** 被测缓存后端。 */
    protected SharedStateCache cache;

    /** 缓存键构建器。 */
    protected CacheKeyBuilder keyBuilder;

    /**
     * 创建被测后端和统一键构建器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    public void setUpContract() {
        cache = createCache();
        keyBuilder = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE),
                NamespacePolicy.stateFallback(STATE_NAMESPACE)));
    }

    /**
     * 创建被测缓存后端。
     *
     * @return 缓存后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    protected abstract SharedStateCache createCache();

    /**
     * 推进后端可观察时间。
     *
     * @param duration 推进时长
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    protected abstract void advanceTime(Duration duration);

    /**
     * put 应支持类型化写入和覆盖更新。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldPutGetAndUpsertTypedValue() {
        CacheKey<String> key = stringKey("upsert");

        cache.put(key, "green", ETERNAL);
        Assert.assertEquals(Optional.of("green"), cache.get(key));

        cache.put(key, "blue", ETERNAL);
        Assert.assertEquals(Optional.of("blue"), cache.get(key));
    }

    /**
     * 可变值应通过 JSON 快照与调用方及每次读取结果隔离。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIsolateMutableValuesThroughJsonRoundTrip() {
        CacheKey<MutableCacheValue> key = keyBuilder.build(
                CACHE_MUTABLE_KEYS, "json-isolation", MutableCacheValue.class);
        MutableCacheValue callerValue = new MutableCacheValue("green");
        cache.put(key, callerValue, ETERNAL);
        callerValue.setName("caller-mutated");

        MutableCacheValue firstRead = cache.get(key).orElseThrow();
        Assert.assertNotSame(callerValue, firstRead);
        Assert.assertEquals("green", firstRead.getName());
        firstRead.setName("reader-mutated");

        MutableCacheValue secondRead = cache.get(key).orElseThrow();
        Assert.assertNotSame(firstRead, secondRead);
        Assert.assertEquals("green", secondRead.getName());
    }

    /**
     * putIfAbsent 应只接受首次写入。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldPutOnlyWhenAbsent() {
        CacheKey<String> key = stringKey("put-if-absent");

        Assert.assertTrue(cache.putIfAbsent(key, "first", ETERNAL));
        Assert.assertFalse(cache.putIfAbsent(key, "second", ETERNAL));
        Assert.assertEquals(Optional.of("first"), cache.get(key));
    }

    /**
     * getOrLoad 未命中时应回源写入，后续读取命中且 TTL 生效。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldLoadThroughAndWriteOnMiss() {
        CacheKey<String> key = stringKey("get-or-load");
        AtomicInteger loaderCalls = new AtomicInteger();

        String value = cache.getOrLoad(key, CacheExpiry.after(Duration.ofSeconds(30L)),
                () -> {
                    loaderCalls.incrementAndGet();
                    return "loaded";
                });

        // 回源加载器只执行一次，且返回值与写入值一致。
        Assert.assertEquals("loaded", value);
        Assert.assertEquals(1, loaderCalls.get());
        Assert.assertEquals(Optional.of("loaded"), cache.get(key));
        // 回源写入应携带调用方传入的到期策略。
        Assert.assertTrue(cache.remainingTtl(key).orElseThrow().ttl().toMillis() > 0L);
    }

    /**
     * getOrLoad 命中时应直接返回缓存值且不调用回源加载器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldReturnCachedValueWithoutLoadingOnHit() {
        CacheKey<String> key = stringKey("get-or-load-hit");
        cache.put(key, "cached", ETERNAL);

        String value = cache.getOrLoad(key, ETERNAL, () -> {
            throw new IllegalStateException("loader must not be called on hit");
        });

        Assert.assertEquals("cached", value);
    }

    /**
     * getOrLoad 的回源加载器返回 null 时应抛出非法参数异常且不产生键。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRejectNullLoaderResultWithoutWriting() {
        CacheKey<String> key = stringKey("get-or-load-null");

        Assert.assertThrows(IllegalArgumentException.class,
                () -> cache.getOrLoad(key, ETERNAL, () -> null));
        Assert.assertFalse(cache.exists(key));
    }

    /**
     * getAndDelete 应一次性返回并消费值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldGetAndDeleteAtomically() {
        CacheKey<String> key = stringKey("consume");
        cache.put(key, "once", ETERNAL);

        Assert.assertEquals(Optional.of("once"), cache.getAndDelete(key));
        Assert.assertEquals(Optional.empty(), cache.getAndDelete(key));
    }

    /**
     * increment 应创建并递增长整型状态。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIncrementLongValue() {
        CacheKey<Long> key = longKey("increment");

        Assert.assertEquals(2L, cache.increment(key, 2L, ETERNAL));
        Assert.assertEquals(5L, cache.increment(key, 3L, ETERNAL));
        Assert.assertEquals(Optional.of(5L), cache.get(key));
    }

    /**
     * compareAndSet 应仅在旧值匹配时更新。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldCompareAndSetAtomically() {
        CacheKey<String> key = stringKey("compare-and-set");
        cache.put(key, "green", ETERNAL);

        Assert.assertFalse(cache.compareAndSet(
                key, new CompareAndSetCommand<>("blue", "red", ETERNAL)));
        Assert.assertTrue(cache.compareAndSet(
                key, new CompareAndSetCommand<>("green", "blue", ETERNAL)));
        Assert.assertEquals(Optional.of("blue"), cache.get(key));
    }

    /**
     * 条件删除仅应移除匹配的值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldCompareAndDeleteAtomically() {
        CacheKey<String> key = stringKey("compare-and-delete");
        cache.put(key, "green", ETERNAL);

        Assert.assertFalse(cache.compareAndDelete(key, "blue"));
        Assert.assertTrue(cache.compareAndDelete(key, "green"));
        Assert.assertFalse(cache.exists(key));
    }

    /**
     * 原子替换应返回旧值并按新策略更新条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldGetAndSetAtomically() {
        CacheKey<String> key = stringKey("get-and-set");
        cache.put(key, "before", ETERNAL);

        Assert.assertEquals(Optional.of("before"), cache.getAndSet(
                key, "after", CacheExpiry.after(Duration.ofSeconds(5L))));
        Assert.assertEquals(Optional.of("after"), cache.get(key));
    }

    /**
     * TTL 生命周期操作应正确反映临时和永久状态。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldManageRemainingTtl() {
        CacheKey<String> key = stringKey("ttl");
        Assert.assertEquals(Optional.empty(), cache.remainingTtl(key));

        cache.put(key, "value", ETERNAL);
        Assert.assertTrue(cache.remainingTtl(key).orElseThrow().isEternal());
        Assert.assertTrue(cache.expire(key, CacheExpiry.after(Duration.ofSeconds(5L))));
        Assert.assertTrue(cache.remainingTtl(key).orElseThrow().ttl().toMillis() > 0L);
        Assert.assertTrue(cache.persist(key));
        Assert.assertTrue(cache.remainingTtl(key).orElseThrow().isEternal());
    }

    /**
     * 强制 TTL 命名空间不得取消过期时间。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPersistForTransientNamespace() {
        CacheKey<String> key = transientStringKey("persist");
        cache.put(key, "value", CacheExpiry.after(Duration.ofSeconds(5L)));
        cache.persist(key);
    }

    /**
     * 批量操作应保留命中结果、独立 TTL 和唯一删除计数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldReadWriteAndDeleteInBatch() {
        CacheKey<String> first = stringKey("batch-first");
        CacheKey<String> second = stringKey("batch-second");
        CacheKey<String> missing = stringKey("batch-missing");
        cache.putAll(Map.of(first, new CacheValue<>("first", ETERNAL), second,
                new CacheValue<>("second", CacheExpiry.after(Duration.ofSeconds(5L)))));

        Assert.assertEquals(Map.of(first, "first", second, "second"),
                cache.getAll(List.of(first, second, missing)));
        Assert.assertTrue(cache.remainingTtl(first).orElseThrow().isEternal());
        Assert.assertTrue(cache.remainingTtl(second).orElseThrow().ttl().toMillis() > 0L);
        Assert.assertEquals(2L, cache.deleteAll(List.of(first, first, second, missing)));
    }

    /**
     * delete 和 exists 应反映真实条目状态。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldDeleteAndReportExistence() {
        CacheKey<String> key = stringKey("delete");
        Assert.assertFalse(cache.exists(key));

        cache.put(key, "value", ETERNAL);
        Assert.assertTrue(cache.exists(key));
        Assert.assertTrue(cache.delete(key));
        Assert.assertFalse(cache.delete(key));
        Assert.assertFalse(cache.exists(key));
    }

    /**
     * 每个条目应独立遵守自己的 TTL。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldExpireEntriesIndependently() {
        CacheKey<String> shortLived = stringKey("short");
        CacheKey<String> longLived = stringKey("long");
        cache.put(shortLived, "short", CacheExpiry.after(Duration.ofSeconds(1L)));
        cache.put(longLived, "long", CacheExpiry.after(Duration.ofSeconds(2L)));

        advanceTime(Duration.ofMillis(1_500L));

        Assert.assertEquals(Optional.empty(), cache.get(shortLived));
        Assert.assertEquals(Optional.of("long"), cache.get(longLived));
    }

    /**
     * 临时状态命名空间必须拒绝永久条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingTtlForTransientNamespace() {
        CacheKey<String> key = keyBuilder.build(STATE_REPLAY_KEYS, "marker", String.class);
        cache.put(key, "used", ETERNAL);
    }

    /**
     * 临时状态命名空间的 putIfAbsent 必须拒绝永久条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingTtlForTransientPutIfAbsent() {
        CacheKey<String> key = transientStringKey("put-if-absent");
        cache.putIfAbsent(key, "used", ETERNAL);
    }

    /**
     * 临时状态命名空间的 increment 必须拒绝永久条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingTtlForTransientIncrement() {
        CacheKey<Long> key = transientLongKey("increment");
        cache.increment(key, 1L, ETERNAL);
    }

    /**
     * 临时状态命名空间的 compareAndSet 必须拒绝永久条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMissingTtlForTransientCompareAndSet() {
        CacheKey<String> key = transientStringKey("compare-and-set");
        cache.put(key, "before", CacheExpiry.after(Duration.ofSeconds(30L)));
        cache.compareAndSet(key, new CompareAndSetCommand<>("before", "after", ETERNAL));
    }

    /**
     * 临时状态命名空间的 getOrLoad 必须先校验到期策略再回源。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRejectEternalGetOrLoadForTransientNamespaceBeforeLoading() {
        CacheKey<String> key = transientStringKey("get-or-load");
        AtomicInteger loaderCalls = new AtomicInteger();

        Assert.assertThrows(IllegalArgumentException.class,
                () -> cache.getOrLoad(key, ETERNAL, () -> {
                    loaderCalls.incrementAndGet();
                    return "loaded";
                }));
        // 校验先于回源执行，loader 未被调用且未产生键。
        Assert.assertEquals(0, loaderCalls.get());
        Assert.assertFalse(cache.exists(key));
    }

    /**
     * 创建普通字符串缓存键。
     *
     * @param identifier 标识符
     * @return 字符串缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<String> stringKey(String identifier) {
        return keyBuilder.build(CACHE_VALUE_KEYS, identifier, String.class);
    }

    /**
     * 创建普通长整型缓存键。
     *
     * @param identifier 标识符
     * @return 长整型缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<Long> longKey(String identifier) {
        return keyBuilder.build(CACHE_COUNTER_KEYS, identifier, Long.class);
    }

    /**
     * 创建临时字符串缓存键。
     *
     * @param identifier 标识符
     * @return 临时字符串缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<String> transientStringKey(String identifier) {
        return keyBuilder.build(STATE_REPLAY_KEYS, identifier, String.class);
    }

    /**
     * 创建临时长整型缓存键。
     *
     * @param identifier 标识符
     * @return 临时长整型缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<Long> transientLongKey(String identifier) {
        return keyBuilder.build(STATE_REPLAY_KEYS, identifier, Long.class);
    }

}
