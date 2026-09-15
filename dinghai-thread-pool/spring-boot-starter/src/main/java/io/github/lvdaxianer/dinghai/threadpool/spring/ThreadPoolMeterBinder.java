package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolSnapshot;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 将受管池的即时容量和任务计数发布为 Micrometer 指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolMeterBinder implements MeterBinder {
    private final ThreadPoolGovernor governor;

    /**
     * 创建受管池指标绑定器。
     *
     * @param governor 应用实例唯一治理器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolMeterBinder(ThreadPoolGovernor governor) {
        this.governor = governor;
    }

    /**
     * 注册每个已配置受管池的动态指标。
     *
     * @param registry Micrometer 注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        governor.snapshots().forEach(snapshot -> registerPool(registry, snapshot.name()));
    }

    /** 注册不持有快照副本的动态指标，从而始终返回最新治理状态。 */
    private void registerPool(MeterRegistry registry, String poolName) {
        Gauge.builder("dinghai.thread.pool.active", governor,
                        source -> snapshot(source, poolName).active())
                .tag("pool", poolName).register(registry);
        Gauge.builder("dinghai.thread.pool.queue.size", governor,
                        source -> snapshot(source, poolName).queueSize())
                .tag("pool", poolName).register(registry);
        Gauge.builder("dinghai.thread.pool.completed", governor,
                        source -> snapshot(source, poolName).completed())
                .tag("pool", poolName).register(registry);
        Gauge.builder("dinghai.thread.pool.rejected", governor,
                        source -> snapshot(source, poolName).rejected())
                .tag("pool", poolName).register(registry);
    }

    /** 从即时快照中定位已在启动期注册的池。 */
    private ThreadPoolSnapshot snapshot(ThreadPoolGovernor source, String poolName) {
        return source.snapshots().stream().filter(snapshot -> poolName.equals(snapshot.name())).findFirst()
                .orElseThrow(() -> new IllegalStateException("thread pool is no longer registered: " + poolName));
    }
}
