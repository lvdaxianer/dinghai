package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 批量缓存写入条目。
 *
 * @param value 缓存值
 * @param expiry 到期策略
 * @param <T> 缓存值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-09-10
 */
public record CacheValue<T>(T value, CacheExpiry expiry) {

    /**
     * 校验批量写入值与到期策略。
     *
     * @param value 缓存值
     * @param expiry 到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    public CacheValue {
        value = Objects.requireNonNull(value, "value");
        expiry = Objects.requireNonNull(expiry, "expiry");
    }
}
