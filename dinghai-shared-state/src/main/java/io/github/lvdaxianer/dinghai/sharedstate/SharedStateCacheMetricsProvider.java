package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 向 Spring 管理组件暴露共享状态缓存指标快照。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class SharedStateCacheMetricsProvider {

    /** 容错共享状态缓存。 */
    private final ResilientSharedStateCache cache;

    /**
     * 创建共享状态指标提供器。
     *
     * @param cache 容错共享状态缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheMetricsProvider(ResilientSharedStateCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /**
     * 获取最新指标快照。
     *
     * @return 指标快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheMetrics getMetrics() {
        return cache.metrics();
    }
}
