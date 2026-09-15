package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.time.Duration;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 周期清理总控 SQLite 中超过保留期的指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernanceRetentionJob {
    private final GovernanceStore store;
    private final Duration retention;

    /**
     * 创建历史数据保留任务。
     *
     * @param store 总控 SQLite 存储
     * @param retention 指标保留期
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public GovernanceRetentionJob(GovernanceStore store, Duration retention) {
        this.store = Objects.requireNonNull(store, "store");
        this.retention = Objects.requireNonNull(retention, "retention");
    }

    /** 每日清理一次历史指标，单实例总控无需分布式锁。 */
    @Scheduled(fixedDelayString = "${dinghai.thread-pool.governance.retention-purge-interval:PT24H}")
    public void purge() {
        store.purgeMetrics(retention);
    }
}
