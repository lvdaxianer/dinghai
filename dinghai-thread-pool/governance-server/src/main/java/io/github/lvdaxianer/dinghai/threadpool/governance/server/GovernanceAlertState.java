package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 总控 SQLite 中持久化的告警生命周期状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record GovernanceAlertState(String alertKey, String status, long firstFiredAt,
                                   long lastFiredAt, String lastValue, Long resolvedAt) {
}
