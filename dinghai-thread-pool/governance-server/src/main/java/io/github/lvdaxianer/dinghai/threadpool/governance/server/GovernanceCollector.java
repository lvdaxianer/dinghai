package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
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
            consumer.accept(page);
            store.commitCursor(instanceId, page.nextCursor());
            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }
}
