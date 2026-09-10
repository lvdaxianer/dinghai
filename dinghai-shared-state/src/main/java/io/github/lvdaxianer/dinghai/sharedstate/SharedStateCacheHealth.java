package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 共享状态缓存健康快照。
 *
 * @param isRedisConfigured 是否配置 Redis
 * @param circuitState Redis 熔断状态
 * @param consecutiveFailures 连续失败次数
 * @param activeBackend 当前逻辑后端
 * @param isDegraded 是否处于一致性降级状态
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record SharedStateCacheHealth(boolean isRedisConfigured,
                                     SharedStateCircuitState circuitState,
                                     long consecutiveFailures,
                                     String activeBackend,
                                     boolean isDegraded) {
}
