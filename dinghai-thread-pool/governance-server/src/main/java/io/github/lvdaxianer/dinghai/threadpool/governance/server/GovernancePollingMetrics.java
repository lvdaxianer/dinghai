package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;

/**
 * 导出总控采集结果的低基数 Micrometer 指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernancePollingMetrics {
    private final MeterRegistry registry;

    /**
     * 创建总控采集指标发布器。
     *
     * @param registry Micrometer 指标注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernancePollingMetrics(MeterRegistry registry) { this.registry = registry; }

    /**
     * 记录成功消费的一页指标。
     *
     * @param instance 实例标识
     * @param page 已消费指标页
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void pageConsumed(String instance, MetricPage page) {
        counter("dinghai.thread.pool.governance.metrics.pages", instance).increment();
        counter("dinghai.thread.pool.governance.metrics.records", instance).increment(page.metrics().size());
    }

    /**
     * 记录成功发布的子控恢复快照。
     *
     * @param instance 实例标识
     * @param snapshot 当前快照
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void snapshotRecovered(String instance, List<?> snapshot) {
        counter("dinghai.thread.pool.governance.recovery.snapshots", instance).increment();
        counter("dinghai.thread.pool.governance.recovery.pools", instance).increment(snapshot.size());
    }

    /**
     * 记录一个子控在本轮无法采集。
     *
     * @param instance 实例标识
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void collectionFailed(String instance) {
        counter("dinghai.thread.pool.governance.collection.failures", instance).increment();
    }

    /** 创建具有受控实例标签的计数器。 */
    private Counter counter(String name, String instance) {
        return Counter.builder(name).tag("instance", instance).register(registry);
    }
}
