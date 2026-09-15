package io.github.lvdaxianer.dinghai.threadpool.spring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 检查子控 SQLite 指标库是否可读。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolMetricStoreHealthIndicator implements HealthIndicator {
    private final SqliteMetricStore store;

    /**
     * 创建 SQLite 健康检查器。
     *
     * @param store 子控指标库
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolMetricStoreHealthIndicator(SqliteMetricStore store) {
        this.store = store;
    }

    /**
     * 通过一个最小分页读取检查 SQLite 可用性。
     *
     * @return 健康状态
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public Health health() {
        try {
            store.page(null, 1);
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
