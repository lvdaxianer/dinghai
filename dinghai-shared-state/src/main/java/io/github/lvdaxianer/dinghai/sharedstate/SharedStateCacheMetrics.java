package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Map;

/**
 * 共享状态缓存运行指标快照。
 *
 * @param totals 全局指标总计
 * @param dimensionCounts 按事件和命名空间维度统计的计数
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record SharedStateCacheMetrics(SharedStateCacheMetricTotals totals,
                                      Map<String, Long> dimensionCounts) {

    /**
     * 防止调用方修改内部指标维度快照。
     *
     * @param totals 全局指标总计
     * @param dimensionCounts 指标维度计数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public SharedStateCacheMetrics {
        dimensionCounts = Map.copyOf(dimensionCounts);
    }

    /**
     * 获取 Redis 操作失败次数。
     *
     * @return Redis 操作失败次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long redisFailureCount() {
        return totals.redisFailureCount();
    }

    /**
     * 获取熔断打开次数。
     *
     * @return 熔断打开次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long circuitOpenCount() {
        return totals.circuitOpenCount();
    }

    /**
     * 获取本地降级次数。
     *
     * @return 本地降级次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long fallbackCount() {
        return totals.fallbackCount();
    }

    /**
     * 获取一致性损失次数。
     *
     * @return 一致性损失次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long consistencyLossCount() {
        return totals.consistencyLossCount();
    }

    /**
     * 获取 Redis 恢复次数。
     *
     * @return Redis 恢复次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long recoveryCount() {
        return totals.recoveryCount();
    }

    /**
     * 获取半开探测次数。
     *
     * @return 半开探测次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long halfOpenCount() {
        return totals.halfOpenCount();
    }

    /**
     * 获取后端选择次数。
     *
     * @return 后端选择次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long backendSelectionCount() {
        return totals.backendSelectionCount();
    }
}
