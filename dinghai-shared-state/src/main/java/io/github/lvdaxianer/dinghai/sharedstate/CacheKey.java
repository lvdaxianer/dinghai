package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 只能由统一构建器创建的类型化缓存键契约。
 *
 * @param <T> 缓存值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public sealed interface CacheKey<T> permits BuiltCacheKey {

    /**
     * 获取 Redis 风格完整键值。
     *
     * @return 完整键值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    String value();

    /**
     * 获取命名空间策略。
     *
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    NamespacePolicy namespace();

    /**
     * 获取声明值类型。
     *
     * @return 声明值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    Class<T> valueType();
}
