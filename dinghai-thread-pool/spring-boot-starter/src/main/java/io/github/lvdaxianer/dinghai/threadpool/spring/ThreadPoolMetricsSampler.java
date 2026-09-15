package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolSnapshot;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 将受管线程池快照写入子控 SQLite 的独立采样任务。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolMetricsSampler {
    private final ThreadPoolGovernor governor;
    private final SqliteMetricStore store;
    private final Duration retention;

    /** 创建采样器。 */
    public ThreadPoolMetricsSampler(ThreadPoolGovernor governor, SqliteMetricStore store, Duration retention) {
        this.governor = governor;
        this.store = store;
        this.retention = retention;
    }

    /** 采集当前全部受管线程池并清理过期数据。 */
    @Scheduled(fixedDelayString = "${dinghai.thread-pool.metrics.sample-interval:PT30S}")
    public void sample() {
        long now = System.currentTimeMillis();
        governor.snapshots().forEach(snapshot -> store.append(metric(snapshot, now)));
        store.purge(retention);
    }

    private ThreadPoolMetric metric(ThreadPoolSnapshot snapshot, long now) {
        return new ThreadPoolMetric(0, now, snapshot.name(), snapshot.active(), snapshot.poolSize(),
                snapshot.queueSize(), snapshot.actualMaximum(), snapshot.submitted(), snapshot.completed(),
                snapshot.rejected(), snapshot.failed(), snapshot.waitMillis(), snapshot.executionMillis(),
                snapshot.priority(), snapshot.reliability());
    }
}
