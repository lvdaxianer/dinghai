package io.github.lvdaxianer.dinghai.dsl.spi;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 有界编译命令模板缓存。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class BoundedCompiledTemplateCache implements CompiledTemplateCache {

    private final BoundedCache<CacheKey, CompiledCommandTemplate> cache;

    /**
     * 创建有界编译模板缓存。
     *
     * @param maximumSize 最大条目数
     * @param ttl 条目 TTL，零表示立即过期
     */
    public BoundedCompiledTemplateCache(int maximumSize, Duration ttl) {
        this.cache = new BoundedCache<>(maximumSize, ttl);
    }

    /**
     * 读取编译模板。
     *
     * @param key 缓存键
     * @return 编译模板
     */
    @Override
    public Optional<CompiledCommandTemplate> get(CacheKey key) {
        return cache.get(Objects.requireNonNull(key, "key"));
    }

    /**
     * 写入编译模板。
     *
     * @param key 缓存键
     * @param template 编译模板
     */
    @Override
    public void put(CacheKey key, CompiledCommandTemplate template) {
        cache.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(template, "template"));
    }

    /**
     * 清空编译模板缓存。
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
