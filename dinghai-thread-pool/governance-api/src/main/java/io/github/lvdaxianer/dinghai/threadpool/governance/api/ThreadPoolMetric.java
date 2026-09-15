package io.github.lvdaxianer.dinghai.threadpool.governance.api;

/**
 * 可由子控采集与总控聚合的线程池指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record ThreadPoolMetric(long metricId, long sampledAt, String poolName, int active,
                               int poolSize, int queueSize, int actualMaximum, long submitted,
                               long completed, long rejected, long failed, long waitMillis,
                               long executionMillis, String priority, String reliability) {
}
