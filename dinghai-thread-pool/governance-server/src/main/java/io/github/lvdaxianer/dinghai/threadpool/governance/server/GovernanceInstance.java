package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 总控 SQLite 中保存的子控实例注册信息。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record GovernanceInstance(String instanceId, String baseUrl, String environment,
                                 boolean enabled, long updatedAt) {
}
