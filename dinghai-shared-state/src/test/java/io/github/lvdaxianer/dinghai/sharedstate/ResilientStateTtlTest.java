package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 临时状态 TTL 降级安全测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class ResilientStateTtlTest {

    /**
     * 无剩余 TTL 信息的 Redis 状态读取不得固定时长预热。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldNotWarmTtlStateWithoutRemainingRedisTtl() {
        // 准备：临时状态命名空间强制要求有效 TTL。
        String namespace = "ttl-state";
        // stateFallback 策略允许降级但不允许固定时长预热。
        NamespacePolicy policy = NamespacePolicy.stateFallback(namespace);
        // 构建该命名空间下的回放状态键。
        // 键值 identifier 模拟一次性的登录态标识。
        CacheKey<String> key = new CacheKeyBuilder(NamespacePolicyRegistry.of(policy))
                .build(new CacheKeyNamespace(namespace, "auth", "replay"),
                        "identifier", String.class);
        // Redis 后端使用 mock。
        SharedStateCache redis = mock(SharedStateCache.class);
        // 本地后端容量 10，足够容纳单键测试数据。
        // Jackson 编解码器与生产后端保持一致。
        CaffeineSharedStateCache caffeine = new CaffeineSharedStateCache(10L,
                new JacksonCacheEnvelopeCodec(new ObjectMapper()));
        // 模拟 Redis 中存在该状态值。
        when(redis.get(key)).thenReturn(Optional.of("used"));
        // 熔断配置使用默认值，本用例不触发熔断。
        ResilientSharedStateCache cache = new ResilientSharedStateCache(
                Optional.of(redis), caffeine,
                new ResilientSharedStateCacheOptions(
                        new SharedStateCacheProperties.CircuitProperties(),
                        System::currentTimeMillis));

        // 执行：读取命中 Redis 返回值。
        Assert.assertEquals(Optional.of("used"), cache.get(key));

        // 断言：契约拿不到剩余 TTL，本地不得固定时长预热。
        // 若按固定时长预热，已消费的一次性状态可能被重放。
        Assert.assertEquals(Optional.empty(), caffeine.get(key));
    }
}
