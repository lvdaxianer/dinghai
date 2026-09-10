package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 统一构建器使用的非公开缓存键实现。
 *
 * @param value Redis 风格完整键值
 * @param namespace 命名空间策略
 * @param valueType 声明值类型
 * @param <T> 缓存值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
record BuiltCacheKey<T>(String value, NamespacePolicy namespace, Class<T> valueType)
        implements CacheKey<T> {

    /**
     * 校验构建器生成的缓存键。
     *
     * @param value Redis 风格完整键值
     * @param namespace 命名空间策略
     * @param valueType 声明值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    BuiltCacheKey {
        // 空白键值无法被后端可靠定位，因此内部构造入口直接拒绝。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cache key value must not be blank");
        }
        namespace = Objects.requireNonNull(namespace, "namespace");
        valueType = Objects.requireNonNull(valueType, "valueType");
    }
}
