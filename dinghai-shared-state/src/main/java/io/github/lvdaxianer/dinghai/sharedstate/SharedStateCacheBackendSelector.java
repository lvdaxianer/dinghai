package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 根据 Redis 配置选择共享状态缓存后端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class SharedStateCacheBackendSelector {

    /**
     * 选择应用使用的共享状态缓存后端。
     *
     * @param properties 共享状态缓存配置
     * @param redis Redis 后端
     * @param caffeine Caffeine 后端
     * @return 已选择的共享状态缓存后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCache select(SharedStateCacheProperties properties,
            SharedStateCache redis, SharedStateCache caffeine) {
        SharedStateCacheProperties validProperties =
                Objects.requireNonNull(properties, "properties");
        // 配置 Redis 时必须选择共享后端；未配置时维持单机兼容模式。
        if (validProperties.getRedis().isConfigured()) {
            return Objects.requireNonNull(redis, "redis");
        }
        return Objects.requireNonNull(caffeine, "caffeine");
    }
}
