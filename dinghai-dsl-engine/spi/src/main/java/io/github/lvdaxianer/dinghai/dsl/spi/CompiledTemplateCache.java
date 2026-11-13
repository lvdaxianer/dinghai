package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Optional;

/**
 * 版本化编译命令模板缓存扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface CompiledTemplateCache {

    /**
     * 读取编译模板。
     *
     * @param key 版本化缓存键
     * @return 缓存命中结果
     */
    Optional<CompiledCommandTemplate> get(CacheKey key);

    /**
     * 写入编译模板。
     *
     * @param key 版本化缓存键
     * @param template 无敏感值编译模板
     */
    void put(CacheKey key, CompiledCommandTemplate template);
}
