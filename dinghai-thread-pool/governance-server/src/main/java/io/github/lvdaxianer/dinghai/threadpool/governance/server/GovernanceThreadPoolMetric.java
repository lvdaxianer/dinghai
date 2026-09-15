package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 总控已采集线程池指标的实例化视图。
 *
 * @param instanceId 子控实例标识
 * @param sampledAt 子控采样时刻
 * @param poolName 线程池名称
 * @param active 活跃任务数
 * @param poolSize 当前线程数
 * @param queueSize 队列深度
 * @param actualMaximum 生效最大线程数
 * @param submitted 累计提交数
 * @param completed 累计完成数
 * @param rejected 累计拒绝数
 * @param failed 累计失败数
 * @param waitMillis 累计等待耗时
 * @param executionMillis 累计执行耗时
 * @param priority 调度优先级
 * @param reliability 可靠性策略
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record GovernanceThreadPoolMetric(String instanceId, long sampledAt, String poolName, int active,
                                         int poolSize, int queueSize, int actualMaximum, long submitted,
                                         long completed, long rejected, long failed, long waitMillis,
                                         long executionMillis, String priority, String reliability) {
}
