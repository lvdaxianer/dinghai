package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.util.Optional;

/**
 * 计划或编译产物缓存扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface PlanCache {

    /**
     * 读取缓存计划。
     *
     * @param key 版本化缓存键
     * @return 缓存命中结果
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Optional<ExecutionPlan> get(CacheKey key);

    /**
     * 写入缓存计划。
     *
     * @param key 版本化缓存键
     * @param plan 不可变执行计划
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    void put(CacheKey key, ExecutionPlan plan);
}
