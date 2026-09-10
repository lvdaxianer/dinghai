package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
     * 读穿加载并同步本地镜像，Redis 故障时降级到本地镜像回源。
     *
     * <p>回源加载器抛出的异常直接上抛，不计入熔断失败。</p>
     *
     * @param key 类型化缓存键
     * @param expiry 回源写入时的到期策略
     * @param loader 回源加载器，不得返回 null
     * @param <T> 缓存值类型
     * @return 缓存命中值或回源加载值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Override
    public <T> T getOrLoad(CacheKey<T> key, CacheExpiry expiry,
            Supplier<? extends T> loader) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.getOrLoad(key, expiry, loader),
                () -> caffeine.getOrLoad(key, localExpiry(key, expiry), loader),
                result -> mirrorRead(key, Optional.of(result))));
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

    @Override
    public <T> boolean compareAndDelete(CacheKey<T> key, T expectedValue) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.compareAndDelete(key, expectedValue),
                () -> caffeine.compareAndDelete(key, expectedValue), ignored -> caffeine.delete(key)));
    }

    @Override
    public <T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.getAndSet(key, value, expiry),
                () -> caffeine.getAndSet(key, value, localExpiry(key, expiry)),
                ignored -> caffeine.put(key, value, localExpiry(key, expiry))));
    }

    @Override
    public Optional<CacheExpiry> remainingTtl(CacheKey<?> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.remainingTtl(key), () -> caffeine.remainingTtl(key), ignored -> { }));
    }

    @Override
    public boolean expire(CacheKey<?> key, CacheExpiry expiry) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.expire(key, expiry), () -> caffeine.expire(key, localExpiry(key, expiry)),
                updated -> mirrorExpiry(key, expiry, updated)));
    }

    @Override
    public boolean persist(CacheKey<?> key) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(
                backend -> backend.persist(key), () -> caffeine.persist(key),
                updated -> mirrorExpiry(key, CacheExpiry.eternal(), updated)));
    }

    @Override
    public <T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys) {
        return getAllFromBackend(keys);
    }

    @Override
    public <T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries) {
        if (entries.isEmpty()) {
            return;
        }
        CacheKey<T> routingKey = routingKey(entries.keySet());
        execution.executeVoid(routingKey, new SharedStateCacheVoidOperationPlan(
                backend -> backend.putAll(entries), () -> caffeine.putAll(entries),
                () -> caffeine.putAll(entries)));
    }

    @Override
    public long deleteAll(Collection<? extends CacheKey<?>> keys) {
        Collection<? extends CacheKey<?>> uniqueKeys = keys.stream().distinct().toList();
        if (uniqueKeys.isEmpty()) {
            return 0L;
        }
        CacheKey<?> routingKey = routingKeyAny(uniqueKeys);
        return execution.execute(routingKey, new SharedStateCacheOperationPlan<>(
                backend -> backend.deleteAll(uniqueKeys), () -> caffeine.deleteAll(uniqueKeys),
                ignored -> caffeine.deleteAll(uniqueKeys)));
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

    /** 将 Redis 确认的 TTL 变更同步到本地镜像。 */
    private void mirrorExpiry(CacheKey<?> key, CacheExpiry expiry, boolean updated) {
        if (updated) {
            caffeine.expire(key, localExpiry(key, expiry));
        } else {
            caffeine.delete(key);
        }
    }

    /** 使用第一个键的熔断路由执行批量读取。 */
    private <T> Map<CacheKey<T>, T> getAllFromBackend(Collection<CacheKey<T>> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        CacheKey<T> routingKey = routingKey(keys);
        return execution.execute(routingKey, new SharedStateCacheOperationPlan<>(
                backend -> backend.getAll(keys), () -> caffeine.getAll(keys),
                result -> mirrorBulkRead(keys, result)));
    }

    /**
     * 选择批量操作的熔断路由键。
     *
     * @param keys 批量操作键
     * @param <T> 值类型
     * @return 分布式强一致键优先的路由键
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    private <T> CacheKey<T> routingKey(Collection<CacheKey<T>> keys) {
        for (CacheKey<T> key : keys) {
            if (isDistributedRequired(key)) {
                return key;
            }
        }
        return keys.iterator().next();
    }

    /** 为不同值类型混合的删除批次选择路由键。 */
    private CacheKey<?> routingKeyAny(Collection<? extends CacheKey<?>> keys) {
        for (CacheKey<?> key : keys) {
            if (isDistributedRequired(key)) {
                return key;
            }
        }
        return keys.iterator().next();
    }

    /** 判断键是否要求 Redis 可用。 */
    private boolean isDistributedRequired(CacheKey<?> key) {
        return key.namespace().fallbackPolicy()
                == NamespaceFallbackPolicy.DISTRIBUTED_REQUIRED;
    }

    /**
     * 按 Redis 批量读取结果更新本地镜像。
     *
     * @param keys 批量读取键
     * @param result Redis 命中结果
     * @param <T> 值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    private <T> void mirrorBulkRead(Collection<CacheKey<T>> keys,
            Map<CacheKey<T>, T> result) {
        keys.forEach(key -> mirrorRead(key, Optional.ofNullable(result.get(key))));
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
        // 单机模式和临时状态必须保留调用方声明的原始有效期。
        if (!execution.isRedisConfigured() || key.namespace().isTtlRequired()) {
            return expiry;
        }
        return expiry.isEternal() ? CacheExpiry.after(properties.getWarmTtl()) : expiry;
    }

}
