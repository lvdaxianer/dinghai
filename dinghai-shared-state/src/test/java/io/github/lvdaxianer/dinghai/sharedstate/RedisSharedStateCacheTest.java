package io.github.lvdaxianer.dinghai.sharedstate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Redis 共享状态后端适配测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class RedisSharedStateCacheTest {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "redis-cache";

    /** 临时状态命名空间。 */
    private static final String STATE_NAMESPACE = "redis-state";

    /** Redis 键路径。 */
    private static final CacheKeyNamespace CACHE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "cache", "value");

    /** 临时 Redis 键路径。 */
    private static final CacheKeyNamespace STATE_KEYS =
            new CacheKeyNamespace(STATE_NAMESPACE, "auth", "state");

    /** Redis 操作模板。 */
    private RedisOperations<String, String> operations;

    /** Redis 值操作。 */
    private ValueOperations<String, String> values;

    /** 缓存信封编解码器。 */
    private CacheEnvelopeCodec codec;

    /** Redis 后端。 */
    private RedisSharedStateCache cache;

    /** 缓存键构建器。 */
    private CacheKeyBuilder keyBuilder;

    /**
     * 创建 Redis 适配测试依赖。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        operations = mock(RedisOperations.class);
        values = mock(ValueOperations.class);
        codec = new JacksonCacheEnvelopeCodec(new ObjectMapper());
        cache = new RedisSharedStateCache(operations, codec);
        keyBuilder = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE),
                NamespacePolicy.stateFallback(STATE_NAMESPACE)));
        when(operations.opsForValue()).thenReturn(values);
    }

    /**
     * Redis 读取应解码统一类型化 JSON 信封。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldDecodeTypedEnvelopeFromRedis() {
        // 步骤 1：构建普通缓存键，并准备 Redis 返回的类型化信封文本。
        // 信封由真实编解码器生成，确保桩数据与线上格式一致。
        CacheKey<String> key = keyBuilder.build(CACHE_KEYS, "theme", String.class);
        when(values.get(key.value())).thenReturn(codec.encode(CacheEnvelope.of(String.class, "green")));

        // 步骤 2：通过后端读取缓存值。
        Optional<String> result = cache.get(key);

        // 步骤 3：断言信封被正确解码为原始字符串值。
        // 同时验证了类型标识与值类型的匹配校验未被误触发。
        Assert.assertEquals(Optional.of("green"), result);
    }

    /**
     * 带 TTL 的 putIfAbsent 应使用 Redis 原子命令。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldUseAtomicRedisPutIfAbsentWithTtl() {
        // 步骤 1：构建临时状态键，并准备 30 秒 TTL。
        CacheKey<String> key = keyBuilder.build(STATE_KEYS, "replay", String.class);
        Duration ttl = Duration.ofSeconds(30L);
        // 步骤 2：模拟 Redis 原子写入成功。
        when(values.setIfAbsent(eq(key.value()), anyString(), eq(ttl))).thenReturn(true);

        // 步骤 3：执行带 TTL 的 putIfAbsent 写入。
        boolean isInserted = cache.putIfAbsent(key, "used", CacheExpiry.after(ttl));

        // 步骤 4：断言写入成功，且 TTL 被原样传递给 Redis 原子命令。
        Assert.assertTrue(isInserted);
        verify(values).setIfAbsent(eq(key.value()), anyString(), eq(ttl));
    }

    /**
     * 一次性消费应通过兼容旧版 Redis 的服务端脚本完成读删。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-02
     */
    @Test
    public void shouldConsumeValueWithAtomicRedisScript() {
        // 步骤 1：构建临时状态键，并准备脚本执行后返回的信封文本。
        CacheKey<String> key = keyBuilder.build(STATE_KEYS, "ticket", String.class);
        String encoded = codec.encode(CacheEnvelope.of(String.class, "ticket-value"));
        when(operations.execute(anyRedisScript(), eq(List.of(key.value())))).thenReturn(encoded);

        // 步骤 2：执行一次性消费（读取并删除）。
        Optional<String> result = cache.getAndDelete(key);

        // 步骤 3：断言返回值被正确解码，且确实通过服务端脚本原子完成。
        Assert.assertEquals(Optional.of("ticket-value"), result);
        verify(operations).execute(anyRedisScript(), eq(List.of(key.value())));
    }

    /**
     * 条件删除和读取替换应使用服务端 Lua 脚本。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldUseAtomicScriptsForDeleteAndReplace() {
        CacheKey<String> key = keyBuilder.build(CACHE_KEYS, "atomic", String.class);
        String previous = codec.encode(CacheEnvelope.of(String.class, "before"));
        when(operations.execute(eq(RedisLuaScripts.COMPARE_AND_DELETE),
                eq(List.of(key.value())), anyString())).thenReturn(1L);
        when(operations.execute(eq(RedisLuaScripts.GET_AND_SET), eq(List.of(key.value())),
                anyString(), eq("1000"))).thenReturn(previous);

        Assert.assertTrue(cache.compareAndDelete(key, "before"));
        Assert.assertEquals(Optional.of("before"), cache.getAndSet(
                key, "after", CacheExpiry.after(Duration.ofSeconds(1L))));
    }

    /**
     * Redis TTL 哨兵值应映射为公开到期策略。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldMapRedisTtlSentinels() {
        CacheKey<String> key = keyBuilder.build(CACHE_KEYS, "ttl", String.class);
        when(operations.getExpire(key.value(), TimeUnit.MILLISECONDS)).thenReturn(-1L);

        Assert.assertTrue(cache.remainingTtl(key).orElseThrow().isEternal());
        verify(operations).getExpire(key.value(), TimeUnit.MILLISECONDS);
    }

    /**
     * 批量读取和删除应分别使用 Redis 原生批量命令。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    @Test
    public void shouldUseBulkReadAndDeleteCommands() {
        CacheKey<String> first = keyBuilder.build(CACHE_KEYS, "first", String.class);
        CacheKey<String> second = keyBuilder.build(CACHE_KEYS, "second", String.class);
        when(values.multiGet(List.of(first.value(), second.value()))).thenReturn(Arrays.asList(
                codec.encode(CacheEnvelope.of(String.class, "first")), null));
        when(operations.delete(List.of(first.value(), second.value()))).thenReturn(1L);

        Assert.assertEquals(Map.of(first, "first"), cache.getAll(List.of(first, second)));
        Assert.assertEquals(1L, cache.deleteAll(List.of(first, first, second)));
    }

    /**
     * 临时状态缺少 TTL 时不得访问 Redis。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectTransientStateWithoutTtl() {
        // 步骤 1：构建强制 TTL 的临时状态键。
        CacheKey<String> key = keyBuilder.build(STATE_KEYS, "missing-ttl", String.class);

        // 步骤 2：以永久有效期写入。
        // 预期：本地校验阶段直接抛出非法参数异常，不会向 Redis 发起任何命令。
        cache.put(key, "used", CacheExpiry.eternal());
    }

    /**
     * Spring Data Redis 访问异常应转换为可熔断后端异常。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldWrapRedisDataAccessFailure() {
        // 步骤 1：构建普通缓存键，并模拟 Redis 连接失败。
        CacheKey<String> key = keyBuilder.build(CACHE_KEYS, "failure", String.class);
        when(values.get(key.value()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // 步骤 2：断言 Spring Data 异常被包装为可熔断的后端异常。
        Assert.assertThrows(RedisBackendException.class, () -> cache.get(key));
    }

    /**
     * 匹配任意 Redis 脚本并保留泛型返回类型。
     *
     * @return Redis 脚本参数匹配器
     * @author lvdaxianer@yeah.net
     * @date 2026-08-02
     */
    @SuppressWarnings("unchecked")
    private RedisScript<String> anyRedisScript() {
        return (RedisScript<String>) org.mockito.ArgumentMatchers.any(RedisScript.class);
    }

    /**
     * 批量写入的管道条目失败应快速抛出后端异常。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastWhenPipelineEntryFails() {
        // 步骤 1：构建两条批量写入条目，并模拟管道结果携带条目级失败。
        Map<CacheKey<String>, CacheValue<String>> entries = Map.of(
                keyBuilder.build(CACHE_KEYS, "batch-ok", String.class),
                new CacheValue<>("ok", CacheExpiry.eternal()),
                keyBuilder.build(CACHE_KEYS, "batch-fail", String.class),
                new CacheValue<>("fail", CacheExpiry.eternal()));
        when(operations.executePipelined(
                org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.SessionCallback.class)))
                .thenReturn(List.of("OK",
                        new org.springframework.data.redis.RedisSystemException("OOM", null)));

        // 步骤 2：断言条目级失败被抛出为可熔断的后端异常，而非静默成功。
        Assert.assertThrows(RedisBackendException.class, () -> cache.putAll(entries));
    }

    /**
     * 批量写入的管道提交失败应快速抛出后端异常。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastWhenPipelineSubmissionFails() {
        // 步骤 1：构建批量写入条目，并模拟管道提交时连接失败。
        Map<CacheKey<String>, CacheValue<String>> entries = Map.of(
                keyBuilder.build(CACHE_KEYS, "batch-down", String.class),
                new CacheValue<>("down", CacheExpiry.eternal()));
        when(operations.executePipelined(
                org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.SessionCallback.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // 步骤 2：断言提交失败被抛出为可熔断的后端异常。
        Assert.assertThrows(RedisBackendException.class, () -> cache.putAll(entries));
    }
}
