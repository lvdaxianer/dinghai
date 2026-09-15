package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 以“聚合成功后再提交 cursor”的顺序采集单个子控。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernanceCollector {
    private final GovernanceStore store;

    /** 创建采集器。 */
    public GovernanceCollector(GovernanceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * 拉取一个实例直到没有下一页，并且只在消费者成功后推进 cursor。
     *
     * @param instanceId 实例标识
     * @param fetcher 指标页读取器
     * @param consumer 实时聚合及指标发布器
     * @throws Exception 读取或聚合失败
     */
    public void collect(String instanceId, MetricPageFetcher fetcher, Consumer<MetricPage> consumer) throws Exception {
        String cursor = store.cursor(instanceId).orElse(null);
        boolean hasMore = true;
        while (hasMore) {
            MetricPage page = fetcher.fetch(cursor);
            store.appendMetrics(instanceId, page.metrics());
            consumer.accept(page);
            store.commitCursor(instanceId, page.nextCursor());
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }

    /**
     * 拉取增量指标；cursor 过期时以子控快照恢复当前可观测性。
     *
     * @param instanceId 子控实例标识
     * @param fetcher 指标页读取器
     * @param snapshotFetcher 当前快照读取器
     * @param metricConsumer 实时指标聚合器
     * @param snapshotConsumer 快照发布器
     * @throws Exception 读取、聚合或快照发布失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void collectWithRecovery(String instanceId, MetricPageFetcher fetcher, SnapshotFetcher snapshotFetcher,
                                    Consumer<MetricPage> metricConsumer, Consumer<List<?>> snapshotConsumer)
            throws Exception {
        try {
            collect(instanceId, fetcher, metricConsumer);
        } catch (CursorExpiredException exception) {
            recoverExpiredCursor(instanceId, exception, snapshotFetcher, snapshotConsumer);
        }
    }

    /** 在快照发布成功后才标记缺口恢复并推进 cursor。 */
    private void recoverExpiredCursor(String instanceId, CursorExpiredException exception,
                                      SnapshotFetcher snapshotFetcher, Consumer<List<?>> snapshotConsumer)
            throws Exception {
        String cursor = store.cursor(instanceId).orElse(null);
        store.recordGap(instanceId, cursor, exception.oldestCursor());
        store.fireAlert(gapAlertKey(instanceId), exception.oldestCursor());
        snapshotConsumer.accept(snapshotFetcher.fetch());
        store.commitCursor(instanceId, exception.oldestCursor());
        store.markLatestGapRecovered(instanceId);
        store.resolveAlert(gapAlertKey(instanceId));
    }

    /** 返回子控历史缺失告警的稳定键。 */
    private String gapAlertKey(String instanceId) { return "collection-gap:" + instanceId; }
}
