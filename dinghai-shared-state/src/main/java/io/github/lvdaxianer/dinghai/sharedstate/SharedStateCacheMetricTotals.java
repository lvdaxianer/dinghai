package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 共享状态缓存全局指标总计。
 *
 * @param redisFailureCount Redis 操作失败次数
 * @param circuitOpenCount 熔断打开次数
 * @param fallbackCount 本地降级次数
 * @param consistencyLossCount 一致性损失次数
 * @param recoveryCount Redis 恢复次数
 * @param halfOpenCount 半开探测次数
 * @param backendSelectionCount 后端选择次数
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record SharedStateCacheMetricTotals(long redisFailureCount,
                                           long circuitOpenCount,
                                           long fallbackCount,
                                           long consistencyLossCount,
                                           long recoveryCount,
                                           long halfOpenCount,
                                           long backendSelectionCount) {
}
