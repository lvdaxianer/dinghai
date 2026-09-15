package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.util.List;

/**
 * 从子控读取当前快照的可替换边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface SnapshotFetcher {
    /**
     * 获取子控当前运行快照。
     *
     * @return 子控快照列表
     * @throws Exception 网络或协议失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    List<?> fetch() throws Exception;
}
