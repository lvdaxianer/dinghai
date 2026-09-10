package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 向 Spring 管理组件暴露共享状态缓存健康快照。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class SharedStateCacheHealthProvider {

    /** 容错共享状态缓存。 */
    private final ResilientSharedStateCache cache;

    /**
     * 创建共享状态健康提供器。
     *
     * @param cache 容错共享状态缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheHealthProvider(ResilientSharedStateCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /**
     * 获取最新健康快照。
     *
     * @return 健康快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheHealth getHealth() {
        return cache.health();
    }
}
