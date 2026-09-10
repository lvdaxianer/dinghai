package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;
import java.util.Optional;

/**
 * Redis 优先、Caffeine 降级的共享状态容错门面。
 *
 * <p>门面集中路由缓存操作，并在 Redis 健康时同步本地镜像。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class ResilientSharedStateCache implements SharedStateCache {

    /** Caffeine 降级后端。 */
    private final CaffeineSharedStateCache caffeine;

    /** 容错配置。 */
    private final SharedStateCacheProperties.CircuitProperties properties;

    /** Redis 优先执行协调器。 */
    private final SharedStateCacheExecution execution;

    /**
     * 创建 Redis 主后端容错门面。
     *
     * @param redis Redis 主后端，不存在时使用本地模式
     * @param caffeine Caffeine 降级后端
     * @param options 容错运行选项
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public ResilientSharedStateCache(Optional<SharedStateCache> redis,
            CaffeineSharedStateCache caffeine,
            ResilientSharedStateCacheOptions options) {
        this.caffeine = Objects.requireNonNull(caffeine, "caffeine");
        this.properties = Objects.requireNonNull(options, "options").circuitProperties();
        this.execution = new SharedStateCacheExecution(redis, caffeine, options);
    }

    /**
     * 读取类型化值并预热本地镜像。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> Optional<T> get(CacheKey<T> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.get(key), () -> caffeine.get(key),
                result -> mirrorRead(key, result)));
    }

    /**
     * 写入类型化值并同步本地镜像。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param expiry 到期策略
     * @param <T> 缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> void put(CacheKey<T> key, T value, CacheExpiry expiry) {
        execution.executeVoid(key, new SharedStateCacheVoidOperationPlan(
                backend -> backend.put(key, value, expiry),
                () -> caffeine.put(key, value, localExpiry(key, expiry)),
                () -> caffeine.put(key, value, localExpiry(key, expiry))));
    }

    /**
     * 不存在时写入值并同步本地镜像。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param expiry 到期策略
     * @param <T> 缓存值类型
     * @return 是否写入成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.putIfAbsent(key, value, expiry),
                () -> caffeine.putIfAbsent(key, value, localExpiry(key, expiry)),
                isInserted -> {
                    // Redis 成功插入时镜像新值，键已存在时清除无法确认的旧镜像。
                    if (isInserted) {
                        caffeine.put(key, value, localExpiry(key, expiry));
                    } else {
                        caffeine.delete(key);
                    }
                }));
    }

    /**
     * 原子读取并删除值，同时删除本地镜像。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 删除前的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> Optional<T> getAndDelete(CacheKey<T> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.getAndDelete(key), () -> caffeine.getAndDelete(key),
                ignored -> caffeine.delete(key)));
    }

    /**
     * 原子增加长整型值并同步本地结果。
     *
     * @param key 长整型键
     * @param delta 增量
     * @param expiry 到期策略
     * @return 增加后的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long increment(CacheKey<Long> key, long delta, CacheExpiry expiry) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.increment(key, delta, expiry),
                () -> caffeine.increment(key, delta, localExpiry(key, expiry)),
                result -> caffeine.put(key, result, localExpiry(key, expiry))));
    }

    /**
     * 原子比较并更新值并同步成功结果。
     *
     * @param key 类型化缓存键
     * @param command 比较更新命令
     * @param <T> 缓存值类型
     * @return 是否更新成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.compareAndSet(key, command),
                () -> caffeine.compareAndSet(key, command),
                isUpdated -> mirrorCompared(key, command, isUpdated)));
    }

    /**
     * 删除值和本地镜像。
     *
     * @param key 类型化键
     * @return 是否删除
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean delete(CacheKey<?> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.delete(key), () -> caffeine.delete(key),
                ignored -> caffeine.delete(key)));
    }

    /**
     * 判断值是否存在。
     *
     * @param key 类型化键
     * @return 是否存在
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean exists(CacheKey<?> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.exists(key), () -> caffeine.exists(key),
                isExists -> {
                    // Redis 明确不存在时删除本地陈旧镜像。
                    if (!isExists) {
                        caffeine.delete(key);
                    }
                }));
    }

    /**
     * 获取健康快照。
     *
     * @return 健康快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheHealth health() {
        return execution.health();
    }

    /**
     * 获取指标快照。
     *
     * @return 指标快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheMetrics metrics() {
        return execution.metrics();
    }

    /**
     * Redis 比较更新成功时镜像本地值。
     *
     * @param key 类型化缓存键
     * @param command 比较更新命令
     * @param isUpdated 是否更新成功
     * @param <T> 值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> void mirrorCompared(CacheKey<T> key,
            CompareAndSetCommand<T> command, boolean isUpdated) {
        // Redis 更新成功时镜像新值，比较失败时清除无法确认的旧镜像。
        if (isUpdated) {
            caffeine.put(key, command.newValue(), localExpiry(key, command.expiry()));
        } else {
            caffeine.delete(key);
        }
    }

    /**
     * 使用配置 TTL 预热本地值。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param <T> 值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> void warm(CacheKey<T> key, T value) {
        caffeine.put(key, value, CacheExpiry.after(properties.getWarmTtl()));
    }

    /**
     * 按 Redis 读取结果更新本地镜像。
     *
     * @param key 类型化缓存键
     * @param result Redis 读取结果
     * @param <T> 值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> void mirrorRead(CacheKey<T> key, Optional<T> result) {
        // TTL 状态无法从当前契约取得剩余时间，因此禁止固定时长预热。
        if (key.namespace().isTtlRequired()) {
            caffeine.delete(key);
        } else if (result.isPresent()) {
            // 普通缓存有值时预热，无值时删除可能陈旧的本地镜像。
            warm(key, result.orElseThrow());
        } else {
            caffeine.delete(key);
        }
    }

    /**
     * 调整本地永久值的预热 TTL。
     *
     * @param key 类型化缓存键
     * @param expiry 原始到期策略
     * @return 本地到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheExpiry localExpiry(CacheKey<?> key, CacheExpiry expiry) {
        // 临时状态必须保留调用方指定的实际有效期。
        if (key.namespace().isTtlRequired()) {
            return expiry;
        }
        return expiry.isEternal() ? CacheExpiry.after(properties.getWarmTtl()) : expiry;
    }

}
