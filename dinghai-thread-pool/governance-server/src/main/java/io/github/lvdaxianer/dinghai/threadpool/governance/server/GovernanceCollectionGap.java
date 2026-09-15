package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 总控检测到子控指标历史缺失时保留的恢复记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record GovernanceCollectionGap(long id, String instanceId, String requestedCursor,
                                      String oldestCursor, long detectedAt, String recoveryStatus) {
}
