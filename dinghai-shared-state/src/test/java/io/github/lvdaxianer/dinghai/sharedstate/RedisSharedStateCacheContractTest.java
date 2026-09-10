package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 后端共享契约测试。
 *
 * <p>通过 `-Ddinghai.redis.contract-test=true` 且本机 Redis 可连接时执行；默认跳过，避免无 Redis
 * 的开发环境阻断构建。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class RedisSharedStateCacheContractTest extends SharedStateCacheIncrementContract {

    /** Redis 契约计数器键路径。 */
    private static final CacheKeyNamespace COUNTER_KEYS =
            new CacheKeyNamespace("contract-cache", "contract", "long-boundary");

    /** 永久有效策略。 */
    private static final CacheExpiry ETERNAL = CacheExpiry.eternal();

    /** 当前测试 Redis 后端。 */
    private static final AtomicReference<RedisSharedStateCache> CACHE = new AtomicReference<>();

    /** 当前测试连接工厂。 */
    private static LettuceConnectionFactory connectionFactory;

    /** 当前测试 Redis 模板。 */
    private static RedisTemplate<String, String> redisTemplate;

    /**
     * Redis 未显式启用或不可连接时跳过整个契约测试类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @BeforeClass
    public static void requireRedis() {
        boolean isEnabled = Boolean.getBoolean("dinghai.redis.contract-test");
        Assume.assumeTrue(isEnabled);
        Assert.assertTrue("explicit Redis contract target is unreachable",
                RedisContractConnectionFactory.isReachable());
    }

    /**
     * 创建可复用的真实 Redis 后端。
     *
     * @return Redis 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    protected SharedStateCache createCache() {
        RedisSharedStateCache result = CACHE.updateAndGet(
                existing -> existing == null ? createRedisCache() : existing);
        clearRedis();
        return result;
    }

    /**
     * 创建指向同一 Redis 的独立后端实例，不清理现有测试数据。
     *
     * @return 独立 Redis 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    protected SharedStateCache createAdditionalCache() {
        return new RedisSharedStateCache(redisTemplate,
                new JacksonCacheEnvelopeCodec(new ObjectMapper()));
    }

    /**
     * 通过真实等待验证 Redis TTL 到期。
     *
     * @param duration 等待时长
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    protected void advanceTime(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            // 恢复中断标记并让测试显式失败，避免静默改变线程状态。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("redis contract wait interrupted", exception);
        }
    }

    /**
     * Redis 计数器应精确保留 Long 最大边界。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIncrementToLongMaximumExactly() {
        CacheKey<Long> key = keyBuilder.build(COUNTER_KEYS, "maximum", Long.class);
        cache.put(key, Long.MAX_VALUE - 1L, ETERNAL);

        long result = cache.increment(key, 1L, ETERNAL);

        Assert.assertEquals(Long.MAX_VALUE, result);
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), cache.get(key).orElseThrow());
    }

    /**
     * Redis 计数器超过 Long 范围时应显式失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRejectLongCounterOverflow() {
        CacheKey<Long> key = keyBuilder.build(COUNTER_KEYS, "overflow", Long.class);
        cache.put(key, Long.MAX_VALUE, ETERNAL);

        Assert.assertThrows(RedisBackendException.class,
                () -> cache.increment(key, 1L, ETERNAL));
    }

    /**
     * Redis 计数器应拒绝错误模式版本的信封。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRejectInvalidCounterEnvelope() {
        assertCounterEnvelopeRejected("invalid-version",
                "{\"schemaVersion\":10,\"typeIdentifier\":\"java.lang.Long\",\"value\":1}");
    }

    /**
     * Redis 计数器应拒绝非整数值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRejectDecimalCounterValue() {
        assertCounterEnvelopeRejected("decimal-value",
                "{\"schemaVersion\":1,\"typeIdentifier\":\"java.lang.Long\",\"value\":1.5}");
    }

    /**
     * Redis 计数器应拒绝已有的越界值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRejectExistingCounterOverflow() {
        assertCounterEnvelopeRejected("existing-overflow",
                "{\"schemaVersion\":1,\"typeIdentifier\":\"java.lang.Long\","
                        + "\"value\":9223372036854775808}");
    }

    /**
     * 关闭真实 Redis 连接工厂。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @AfterClass
    public static void closeRedis() {
        // 契约测试被跳过时连接工厂未创建，无需关闭。
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 创建并初始化 Redis 模板和后端。
     *
     * @return Redis 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static RedisSharedStateCache createRedisCache() {
        connectionFactory = RedisContractConnectionFactory.create();
        connectionFactory.afterPropertiesSet();
        RedisTemplate<String, String> template = new RedisTemplate<>();
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        redisTemplate = template;
        return new RedisSharedStateCache(template, new JacksonCacheEnvelopeCodec(new ObjectMapper()));
    }

    /**
     * 清理测试 Redis 数据库。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static void clearRedis() {
        deleteKeys("dinghai:contract:*");
    }

    /**
     * 删除符合模式的 Redis 测试键。
     *
     * @param pattern Redis 键模式
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        // 没有匹配键时无需调用 Redis 删除。
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 写入非法信封并断言递增失败。
     *
     * @param identifier 测试键标识符
     * @param envelope 非法 JSON 信封
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void assertCounterEnvelopeRejected(String identifier, String envelope) {
        CacheKey<Long> key = keyBuilder.build(COUNTER_KEYS, identifier, Long.class);
        redisTemplate.opsForValue().set(key.value(), envelope);
        Assert.assertThrows(RedisBackendException.class,
                () -> cache.increment(key, 1L, ETERNAL));
    }

}
