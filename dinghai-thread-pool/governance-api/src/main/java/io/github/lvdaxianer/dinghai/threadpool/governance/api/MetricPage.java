package io.github.lvdaxianer.dinghai.threadpool.governance.api;

import java.util.List;

/**
 * 子控的增量指标分页响应。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record MetricPage(String nextCursor, boolean hasMore, String oldestCursor,
                         List<ThreadPoolMetric> metrics) {
}
