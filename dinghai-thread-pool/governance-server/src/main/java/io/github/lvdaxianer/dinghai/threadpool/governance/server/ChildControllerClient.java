package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import java.util.List;
import java.util.Map;

/**
 * 总控读取一个子控指标与快照的协议边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public interface ChildControllerClient {
    /**
     * 读取一页增量指标。
     *
     * @param cursor 已确认 cursor
     * @param limit 有界页大小
     * @return 子控指标页
     * @throws Exception 网络或协议失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    MetricPage fetch(String cursor, int limit) throws Exception;

    /**
     * 读取当前快照。
     *
     * @return 结构化快照列表
     * @throws Exception 网络或协议失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    List<Map<String, Object>> fetchSnapshot() throws Exception;
}
