package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;

/**
 * 从一个子控实例读取指标页的可替换边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface MetricPageFetcher {

    /**
     * 获取指定 cursor 之后的一页指标。
     *
     * @param cursor 已确认 cursor，可为空
     * @return 指标页
     * @throws Exception 网络或协议失败
     */
    MetricPage fetch(String cursor) throws Exception;
}
