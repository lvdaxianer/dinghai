package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 缓存比较并更新命令。
 *
 * @param expectedValue 期望旧值
 * @param newValue 新值
 * @param expiry 更新后的到期策略
 * @param <T> 缓存值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record CompareAndSetCommand<T>(T expectedValue, T newValue, CacheExpiry expiry) {

    /**
     * 校验比较并更新命令。
     *
     * @param expectedValue 期望旧值
     * @param newValue 新值
     * @param expiry 更新后的到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CompareAndSetCommand {
        expectedValue = Objects.requireNonNull(expectedValue, "expectedValue");
        newValue = Objects.requireNonNull(newValue, "newValue");
        expiry = Objects.requireNonNull(expiry, "expiry");
    }
}
