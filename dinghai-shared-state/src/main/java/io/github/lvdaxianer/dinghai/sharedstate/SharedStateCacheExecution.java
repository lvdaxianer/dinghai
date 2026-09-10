package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;
import java.util.Optional;

/**
 * Redis 优先缓存操作的熔断、降级和恢复执行协调器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class SharedStateCacheExecution {

    /** Redis 后端名称。 */
    private static final String REDIS_BACKEND = "redis";

    /** Caffeine 后端名称。 */
    private static final String CAFFEINE_BACKEND = "caffeine";

    /** Redis 主后端。 */
    private final Optional<SharedStateCache> redis;

    /** Caffeine 降级后端。 */
    private final CaffeineSharedStateCache caffeine;

    /** Redis 熔断状态机。 */
    private final RedisCircuitBreaker circuitBreaker;

    /** 日志和指标收集器。 */
    private final SharedStateCacheObservability observability =
            new SharedStateCacheObservability();

    /** 降级写入与恢复清理的同步锁。 */
    private final Object recoveryLock = new Object();

    /**
     * 创建缓存执行协调器。
     *
     * @param redis Redis 主后端
     * @param caffeine Caffeine 降级后端
     * @param options 容错运行选项
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheExecution(Optional<SharedStateCache> redis,
            CaffeineSharedStateCache caffeine, ResilientSharedStateCacheOptions options) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.caffeine = Objects.requireNonNull(caffeine, "caffeine");
        ResilientSharedStateCacheOptions validOptions =
                Objects.requireNonNull(options, "options");
        SharedStateCacheProperties.CircuitProperties properties =
                validOptions.circuitProperties();
        this.circuitBreaker = new RedisCircuitBreaker(properties.getFailureThreshold(),
                properties.getOpenDuration(), validOptions.clock());
        // 无 Redis 时只记录一次本地模式，运行期不重复记录降级日志。
        if (redis.isEmpty()) {
            observability.localOnly();
        } else {
            // Redis 已配置时记录共享状态后端选择。
            observability.redisSelected();
        }
    }

    /**
     * 执行 Redis 优先操作。
     *
     * @param key 类型化缓存键
     * @param plan Redis、本地和成功镜像操作计划
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 操作结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T, R> R execute(CacheKey<T> key, SharedStateCacheOperationPlan<R> plan) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        SharedStateCacheOperationPlan<R> validPlan = Objects.requireNonNull(plan, "plan");
        // 未配置 Redis 时执行单机兼容策略。
        if (redis.isEmpty()) {
            return executeLocalMode(validKey, validPlan.localOperation());
        }
        RedisCircuitBreaker.RequestTicket ticket = circuitBreaker.request();
        Optional<R> rejected = tryRejectByCircuit(validKey, validPlan.localOperation(), ticket);
        // 熔断拒绝时直接返回本地降级结果。
        if (rejected.isPresent()) {
            return rejected.orElseThrow();
        }
        RedisResult<R> result = executeRedis(validKey, validPlan, ticket);
        // Redis 成功结果需要完成状态恢复和本地镜像。
        if (result.isRedisSucceeded()) {
            completeRedisSuccess(ticket, validPlan.successOperation(), result.value());
        }
        return result.value();
    }

    /**
     * 处理熔断器放行前的半开记录与拒绝决策。
     *
     * @param key 类型化缓存键
     * @param localOperation 本地降级操作
     * @param ticket Redis 请求票据
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 熔断拒绝时返回本地降级结果，放行时返回空
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> Optional<R> tryRejectByCircuit(CacheKey<T> key,
            LocalCacheOperation<R> localOperation,
            RedisCircuitBreaker.RequestTicket ticket) {
        // 熔断窗口结束时记录唯一半开探测请求。
        if (ticket.decision() == RedisCircuitBreaker.RequestDecision.PROBE) {
            observability.halfOpen(key);
        }
        // 熔断拒绝 Redis 请求时按命名空间策略处理。
        if (ticket.decision() == RedisCircuitBreaker.RequestDecision.REJECT) {
            return Optional.of(fallback(key, localOperation, ticket));
        }
        return Optional.empty();
    }

    /**
     * 执行无返回值 Redis 优先操作。
     *
     * @param key 类型化缓存键
     * @param plan Redis、本地和成功镜像操作计划
     * @param <T> 键类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> void executeVoid(CacheKey<T> key, SharedStateCacheVoidOperationPlan plan) {
        SharedStateCacheVoidOperationPlan validPlan = Objects.requireNonNull(plan, "plan");
        execute(key, new SharedStateCacheOperationPlan<>(backend -> {
            validPlan.redisOperation().accept(backend);
            return Boolean.TRUE;
        }, () -> {
            validPlan.localOperation().run();
            return Boolean.TRUE;
        }, ignored -> validPlan.successOperation().run()));
    }

    /**
     * 获取健康快照。
     *
     * @return 健康快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheHealth health() {
        SharedStateCircuitState state = circuitBreaker.state();
        String backend = redis.isEmpty() || state != SharedStateCircuitState.CLOSED
                ? CAFFEINE_BACKEND : REDIS_BACKEND;
        long failures = circuitBreaker.failures();
        return new SharedStateCacheHealth(redis.isPresent(), state, failures, backend,
                redis.isEmpty() || failures > 0L
                        || state != SharedStateCircuitState.CLOSED);
    }

    /**
     * 获取指标快照。
     *
     * @return 指标快照
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheMetrics metrics() {
        return observability.metrics();
    }

    /**
     * 执行单次 Redis 操作并只捕获 Redis 后端异常。
     *
     * @param key 类型化缓存键
     * @param plan 缓存操作计划
     * @param ticket Redis 请求票据
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return Redis 或本地结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> RedisResult<R> executeRedis(CacheKey<T> key,
            SharedStateCacheOperationPlan<R> plan,
            RedisCircuitBreaker.RequestTicket ticket) {
        try {
            return new RedisResult<>(plan.redisOperation().apply(redis.orElseThrow()), true);
        } catch (RedisBackendException exception) {
            RedisFailure failure = new RedisFailure(exception, ticket);
            return new RedisResult<>(handleFailure(key, plan.localOperation(), failure), false);
        }
    }

    /**
     * 处理 Redis 后端操作失败。
     *
     * @param key 类型化缓存键
     * @param localOperation 本地操作
     * @param failure Redis 故障上下文
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 本地结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> R handleFailure(CacheKey<T> key, LocalCacheOperation<R> localOperation,
            RedisFailure failure) {
        synchronized (recoveryLock) {
            observability.redisFailure(key, failure.exception());
            RedisCircuitBreaker.FailureResult result =
                    circuitBreaker.recordFailure(failure.ticket());
            // 本次失败打开熔断器时记录状态转换。
            if (result.isOpened()) {
                observability.circuitOpened(key, circuitBreaker.failures());
            }
            // 分布式强一致命名空间在 Redis 故障时直接失败关闭。
            if (isDistributedRequired(key)) {
                throw new SharedStateUnavailableException(
                        key.namespace().name(), failure.exception());
            }
            return executeFallback(key, localOperation, result.fallbackTicket());
        }
    }

    /**
     * 执行本地降级并防止跨代降级值残留。
     *
     * @param key 类型化缓存键
     * @param localOperation 本地操作
     * @param ticket 降级票据
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 本地结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> R fallback(CacheKey<T> key, LocalCacheOperation<R> localOperation,
            RedisCircuitBreaker.RequestTicket ticket) {
        // 分布式强一致命名空间禁止进入 Caffeine。
        if (isDistributedRequired(key)) {
            throw new SharedStateUnavailableException(key.namespace().name());
        }
        synchronized (recoveryLock) {
            return executeFallback(key, localOperation, ticket);
        }
    }

    /**
     * 在恢复锁内执行本地降级并复核故障代次。
     *
     * @param key 类型化缓存键
     * @param localOperation 本地操作
     * @param ticket 降级票据
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 本地结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> R executeFallback(CacheKey<T> key, LocalCacheOperation<R> localOperation,
            RedisCircuitBreaker.RequestTicket ticket) {
        observability.fallback(key);
        R result = localOperation.get();
        // 已跨代或已恢复时删除本次操作可能留下的本地值。
        if (!circuitBreaker.retainFallback(ticket)) {
            caffeine.delete(key);
        }
        return result;
    }

    /**
     * 执行未配置 Redis 时的单机兼容模式。
     *
     * @param key 类型化缓存键
     * @param localOperation 本地操作
     * @param <T> 键类型
     * @param <R> 结果类型
     * @return 本地结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T, R> R executeLocalMode(CacheKey<T> key,
            LocalCacheOperation<R> localOperation) {
        // 单机模式同样禁止分布式强一致状态降级。
        if (isDistributedRequired(key)) {
            throw new SharedStateUnavailableException(key.namespace().name());
        }
        // 临时状态在单机模式下明确记录跨节点一致性损失。
        if (key.namespace().fallbackPolicy() == NamespaceFallbackPolicy.STATE_FALLBACK) {
            observability.localOnlyState(key);
        }
        return localOperation.get();
    }

    /**
     * Redis 同代成功时完成恢复清理和本地镜像。
     *
     * @param ticket Redis 请求票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <R> void completeRedisSuccess(RedisCircuitBreaker.RequestTicket ticket,
            CacheSuccessOperation<R> successOperation, R value) {
        synchronized (recoveryLock) {
            // 旧代次成功不得覆盖当前故障代次的本地降级结果。
            if (!circuitBreaker.isCurrent(ticket)) {
                return;
            }
            // 同代成功清理故障状态及全部降级镜像。
            if (circuitBreaker.recordSuccess(ticket)) {
                caffeine.invalidateAll();
                observability.recovered();
            }
            successOperation.accept(value);
        }
    }

    /**
     * 判断命名空间是否禁止本地降级。
     *
     * @param key 类型化缓存键
     * @return true 表示必须使用分布式后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean isDistributedRequired(CacheKey<?> key) {
        return key.namespace().fallbackPolicy() == NamespaceFallbackPolicy.DISTRIBUTED_REQUIRED;
    }

    /**
     * Redis 后端故障上下文。
     *
     * @param exception Redis 后端异常
     * @param ticket 原请求票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private record RedisFailure(RedisBackendException exception,
                                RedisCircuitBreaker.RequestTicket ticket) { }

    /**
     * 区分 Redis 成功和本地降级结果。
     *
     * @param value 操作值
     * @param isRedisSucceeded 是否由 Redis 成功返回
     * @param <R> 结果类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private record RedisResult<R>(R value, boolean isRedisSucceeded) { }
}
