package io.github.lvdaxianer.dinghai.sharedstate;


/**
 * 返回值缓存操作计划。
 *
 * @param redisOperation Redis 操作
 * @param localOperation Caffeine 操作
 * @param successOperation Redis 成功镜像操作
 * @param <R> 返回值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
record SharedStateCacheOperationPlan<R>(RedisCacheOperation<R> redisOperation,
                                        LocalCacheOperation<R> localOperation,
                                        CacheSuccessOperation<R> successOperation) {
}

/**
 * 无返回值缓存操作计划。
 *
 * @param redisOperation Redis 操作
 * @param localOperation Caffeine 操作
 * @param successOperation Redis 成功镜像操作
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
record SharedStateCacheVoidOperationPlan(RedisCacheVoidOperation redisOperation,
                                         Runnable localOperation,
                                         Runnable successOperation) {
}

/**
 * Redis 返回值缓存操作。
 *
 * @param <R> 操作返回值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@FunctionalInterface
interface RedisCacheOperation<R> {

    /**
     * 执行 Redis 操作。
     *
     * @param backend Redis 后端
     * @return 操作结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    R apply(SharedStateCache backend);
}

/**
 * Redis 无返回值缓存操作。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@FunctionalInterface
interface RedisCacheVoidOperation {

    /**
     * 执行 Redis 操作。
     *
     * @param backend Redis 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void accept(SharedStateCache backend);
}

/**
 * Caffeine 返回值缓存操作。
 *
 * @param <R> 操作返回值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@FunctionalInterface
interface LocalCacheOperation<R> {

    /**
     * 执行 Caffeine 操作。
     *
     * @return 操作结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    R get();
}

/**
 * Redis 成功后的本地镜像操作。
 *
 * @param <R> Redis 操作返回值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@FunctionalInterface
interface CacheSuccessOperation<R> {

    /**
     * 执行本地镜像操作。
     *
     * @param result Redis 操作结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void accept(R result);
}
