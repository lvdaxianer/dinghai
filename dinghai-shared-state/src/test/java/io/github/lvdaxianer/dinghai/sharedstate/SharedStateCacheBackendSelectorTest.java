package io.github.lvdaxianer.dinghai.sharedstate;

import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.mock;

/**
 * 共享状态缓存后端选择测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class SharedStateCacheBackendSelectorTest {

    /**
     * 未配置 Redis 时应继续使用 Caffeine。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldSelectCaffeineWhenRedisIsAbsent() {
        // 准备：两个后端候选与未配置 Redis 的属性。
        SharedStateCache caffeine = mock(SharedStateCache.class);
        SharedStateCache redis = mock(SharedStateCache.class);
        SharedStateCacheProperties properties = new SharedStateCacheProperties();

        // 执行：选择后端。
        SharedStateCache selected = new SharedStateCacheBackendSelector()
                .select(properties, redis, caffeine);

        // 断言：回退到 Caffeine。
        Assert.assertSame(caffeine, selected);
    }

    /**
     * Redis 配置应使用 Redis 后端。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldSelectRedisWhenRedisIsConfigured() {
        // 准备：两个后端候选，属性中配置了 Redis 主机。
        SharedStateCache caffeine = mock(SharedStateCache.class);
        SharedStateCache redis = mock(SharedStateCache.class);
        SharedStateCacheProperties properties = new SharedStateCacheProperties();
        properties.getRedis().setHost("127.0.0.1");

        // 执行：选择后端。
        SharedStateCache selected = new SharedStateCacheBackendSelector()
                .select(properties, redis, caffeine);

        // 断言：选中 Redis 后端。
        Assert.assertSame(redis, selected);
    }

}
