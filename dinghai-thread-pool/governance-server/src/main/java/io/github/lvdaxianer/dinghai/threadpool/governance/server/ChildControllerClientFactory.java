package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 为每个已注册子控创建独立 HTTP 客户端的边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface ChildControllerClientFactory {
    /**
     * 创建实例专属客户端。
     *
     * @param instance 已注册子控实例
     * @return 子控客户端
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    ChildControllerClient create(GovernanceInstance instance);
}
