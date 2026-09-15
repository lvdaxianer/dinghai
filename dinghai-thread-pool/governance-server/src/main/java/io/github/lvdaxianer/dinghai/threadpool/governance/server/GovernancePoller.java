package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 单实例总控的定时子控采集任务。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernancePoller {
    private final GovernanceStore store;
    private final GovernanceCollector collector;
    private final ChildControllerClientFactory clients;
    private final GovernancePollingMetrics metrics;
    private final int pageLimit;

    /**
     * 创建总控轮询器。
     *
     * @param store 控制面存储
     * @param collector cursor 采集器
     * @param clients 子控客户端工厂
     * @param metrics 采集指标发布器
     * @param pageLimit 有界指标页大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernancePoller(GovernanceStore store, GovernanceCollector collector,
                            ChildControllerClientFactory clients, GovernancePollingMetrics metrics,
                            int pageLimit) {
        this.store = Objects.requireNonNull(store, "store");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (pageLimit < 1) {
            throw new IllegalArgumentException("governance page limit must be positive");
        }
        this.pageLimit = pageLimit;
    }

    /** 轮询全部已注册子控，隔离单个实例的网络或协议失败。 */
    @Scheduled(fixedDelayString = "${dinghai.thread-pool.governance.poll-interval:PT30S}")
    public void poll() {
        store.instances().forEach(this::collectOne);
    }

    /** 采集一个子控实例，并用指标保留可恢复失败的诊断信号。 */
    private void collectOne(GovernanceInstance instance) {
        try {
            ChildControllerClient client = clients.create(instance);
            collector.collectWithRecovery(instance.instanceId(),
                    cursor -> client.fetch(cursor, pageLimit), client::fetchSnapshot,
                    page -> metrics.pageConsumed(instance.instanceId(), page),
                    snapshot -> metrics.snapshotRecovered(instance.instanceId(), snapshot));
            store.resolveAlert(failureAlertKey(instance.instanceId()));
        } catch (Exception exception) {
            store.fireAlert(failureAlertKey(instance.instanceId()), exception.getClass().getSimpleName());
            metrics.collectionFailed(instance.instanceId());
        }
    }

    /** 返回子控不可达告警的稳定键。 */
    private String failureAlertKey(String instanceId) { return "collection-failure:" + instanceId; }
}
