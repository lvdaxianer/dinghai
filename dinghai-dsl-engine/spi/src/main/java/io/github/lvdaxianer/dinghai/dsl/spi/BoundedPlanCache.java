package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 有界计划缓存。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class BoundedPlanCache implements PlanCache {

    private final BoundedCache<CacheKey, ExecutionPlan> cache;

    /**
     * 创建有界计划缓存。
     *
     * @param maximumSize 最大条目数
     * @param ttl 条目 TTL，零表示立即过期
     */
    public BoundedPlanCache(int maximumSize, Duration ttl) {
        this.cache = new BoundedCache<>(maximumSize, ttl);
    }

    /**
     * 读取计划。
     *
     * @param key 缓存键
     * @return 计划
     */
    @Override
    public Optional<ExecutionPlan> get(CacheKey key) {
        return cache.get(Objects.requireNonNull(key, "key"));
    }

    /**
     * 写入计划。
     *
     * @param key 缓存键
     * @param plan 计划
     */
    @Override
    public void put(CacheKey key, ExecutionPlan plan) {
        cache.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(plan, "plan"));
    }

    /**
     * 清空计划缓存。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 返回当前条目数。
     *
     * @return 条目数
     */
    public int size() {
        return cache.size();
    }
}
