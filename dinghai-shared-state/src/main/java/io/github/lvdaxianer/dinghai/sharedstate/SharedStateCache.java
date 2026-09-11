package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
     * 读取缓存值，未命中时回源加载并按到期策略写入。
     *
     * <p>命中时直接返回缓存值且不调用回源加载器；回源加载器返回 null 时抛出
     * {@link IllegalArgumentException} 且不写入缓存；回源加载器自身抛出的异常直接上抛。
     * 并发语义由后端实现声明，契约不保证跨进程单飞。</p>
     *
     * @param key    类型化缓存键
     * @param expiry 回源写入时的到期策略
     * @param loader 回源加载器，不得返回 null
     * @param <T>    缓存值类型
     * @return 缓存命中值或回源加载值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    <T> T getOrLoad(CacheKey<T> key, CacheExpiry expiry, Supplier<? extends T> loader);

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

    /** 原子删除与期望值匹配的缓存值。 */
    <T> boolean compareAndDelete(CacheKey<T> key, T expectedValue);

    /** 原子读取旧值并写入新值。 */
    <T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry);

    /**
     * 原子读取缓存值并把键的有效期重置为给定到期策略（滑动续期）。
     *
     * <p>键不存在时返回空且不创建键；对永久键调用时返回原值并把键转为给定到期策略；
     * {@code expiry} 为 {@link CacheExpiry#eternal()} 时等效于续期为永久。强制 TTL
     * 命名空间允许携带正数 TTL 续期，但永久续期仍会被拒绝并抛出
     * {@link IllegalArgumentException}。</p>
     *
     * @param key    类型化缓存键
     * @param expiry 续期后的到期策略
     * @param <T>    缓存值类型
     * @return 续期前的缓存值，键不存在时为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    <T> Optional<T> getAndExpire(CacheKey<T> key, CacheExpiry expiry);

    /** 查询键的剩余有效期。 */
    Optional<CacheExpiry> remainingTtl(CacheKey<?> key);

    /** 为已有键设置新的有效期。 */
    boolean expire(CacheKey<?> key, CacheExpiry expiry);

    /** 取消普通命名空间键的有效期。 */
    boolean persist(CacheKey<?> key);

    /** 批量读取命中的类型化缓存值。 */
    <T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys);

    /** 按条目独立到期策略批量写入值。批量操作不保证原子性；任一条目写入失败时抛出异常，失败后缓存可能处于部分写入状态。 */
    <T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries);

    /** 批量删除键并返回实际删除数。 */
    long deleteAll(Collection<? extends CacheKey<?>> keys);

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
