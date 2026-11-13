package io.github.lvdaxianer.dinghai.dsl.spi;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 有界 AST 缓存。
 *
 * <p>宿主只能写入不含参数值、凭据和身份令牌的 AST。</p>
 *
 * @param <T> AST 类型
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class BoundedAstCache<T> {

    private final BoundedCache<CacheKey, T> cache;

    /**
     * 创建有界 AST 缓存。
     *
     * @param maximumSize 最大条目数
     * @param ttl 条目 TTL，零表示立即过期
     */
    public BoundedAstCache(int maximumSize, Duration ttl) {
        this.cache = new BoundedCache<>(maximumSize, ttl);
    }

    /**
     * 读取 AST。
     *
     * @param key 缓存键
     * @return AST
     */
    public Optional<T> get(CacheKey key) {
        return cache.get(Objects.requireNonNull(key, "key"));
    }

    /**
     * 写入 AST。
     *
     * @param key 缓存键
     * @param ast 不含敏感值的 AST
     */
    public void put(CacheKey key, T ast) {
        cache.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(ast, "ast"));
    }

    /**
     * 清空 AST 缓存。
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
