package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 共享状态容错门面的运行选项。
 *
 * @param circuitProperties 熔断配置
 * @param clock 毫秒时间源
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record ResilientSharedStateCacheOptions(
        SharedStateCacheProperties.CircuitProperties circuitProperties,
        LongSupplier clock) {

    /**
     * 校验共享状态容错运行选项。
     *
     * @param circuitProperties 熔断配置
     * @param clock 毫秒时间源
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public ResilientSharedStateCacheOptions {
        circuitProperties = Objects.requireNonNull(circuitProperties, "circuitProperties");
        clock = Objects.requireNonNull(clock, "clock");
    }
}
