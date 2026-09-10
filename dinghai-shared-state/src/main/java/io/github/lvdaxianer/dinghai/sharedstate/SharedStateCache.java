package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Optional;

/**
 * 后端中立的类型化共享缓存和临时状态契约。
 *
 * <p>原子操作的具体一致性范围由后端实现声明；Caffeine 仅保证单进程原子性。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public interface SharedStateCache {

    /**
     * 读取类型化缓存值。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 缓存值，不存在时为空
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> Optional<T> get(CacheKey<T> key);

    /**
     * 新增或覆盖缓存值。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> void put(CacheKey<T> key, T value, CacheExpiry expiry);

    /**
     * 仅在键不存在时写入缓存值。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @return true 表示写入成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry);

    /**
     * 原子读取并删除缓存值。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 删除前的缓存值，不存在时为空
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> Optional<T> getAndDelete(CacheKey<T> key);

    /**
     * 原子增加长整型缓存值。
     *
     * @param key    长整型缓存键
     * @param delta  增量
     * @param expiry 到期策略
     * @return 增加后的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    long increment(CacheKey<Long> key, long delta, CacheExpiry expiry);

    /**
     * 原子比较并更新缓存值。
     *
     * @param key           类型化缓存键
     * @param command       比较并更新命令
     * @param <T>           缓存值类型
     * @return true 表示比较成功并完成更新
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command);

    /**
     * 删除缓存值。
     *
     * @param key 缓存键
     * @return true 表示删除了现有值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    boolean delete(CacheKey<?> key);

    /**
     * 判断缓存键是否存在。
     *
     * @param key 缓存键
     * @return true 表示存在
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    boolean exists(CacheKey<?> key);
}
