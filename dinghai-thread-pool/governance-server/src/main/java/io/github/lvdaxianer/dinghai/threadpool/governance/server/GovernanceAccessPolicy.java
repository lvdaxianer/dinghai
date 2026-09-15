package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 由宿主应用实现的总控管理 API 授权边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface GovernanceAccessPolicy {
    /**
     * 判断当前请求是否允许管理总控。
     *
     * @return 当前请求是否已授权
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    boolean isAllowed();
}
