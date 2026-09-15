package io.github.lvdaxianer.dinghai.threadpool.spring;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dinghai 线程池治理配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@ConfigurationProperties("dinghai.thread-pool")
public class ThreadPoolProperties {
    private boolean enabled;
    private boolean endpointEnabled;
    private Integer processThreadCapacity;
    private int managedWorkerBudget = 1;
    private Map<String, Pool> pools = new LinkedHashMap<>();
    private Metrics metrics = new Metrics();

    /** @return 是否启用治理器 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用治理器 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 是否暴露子控只读端点 */
    public boolean isEndpointEnabled() { return endpointEnabled; }

    /** @param endpointEnabled 是否暴露子控只读端点 */
    public void setEndpointEnabled(boolean endpointEnabled) { this.endpointEnabled = endpointEnabled; }

    /** @return 应用进程可承受的总线程容量声明 */
    public Integer getProcessThreadCapacity() { return processThreadCapacity; }

    /** @param processThreadCapacity 应用进程可承受的总线程容量声明 */
    public void setProcessThreadCapacity(Integer processThreadCapacity) {
        this.processThreadCapacity = processThreadCapacity;
    }

    /** @return 受管 worker 预算 */
    public int getManagedWorkerBudget() { return managedWorkerBudget; }

    /** @param managedWorkerBudget 受管 worker 预算 */
    public void setManagedWorkerBudget(int managedWorkerBudget) { this.managedWorkerBudget = managedWorkerBudget; }

    /** @return 按名称定义的受管线程池 */
    public Map<String, Pool> getPools() { return pools; }

    /** @param pools 按名称定义的受管线程池 */
    public void setPools(Map<String, Pool> pools) { this.pools = pools; }

    /** @return 指标存储配置 */
    public Metrics getMetrics() {
        return metrics;
    }

    /** @param metrics 指标存储配置 */
    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 子控 SQLite 指标配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public static class Metrics {
        private Path sqlitePath = Path.of("dinghai-thread-pool.db");
        private Duration retention = Duration.ofHours(24);
        private Duration sampleInterval = Duration.ofSeconds(30);
        private int pageLimitMax = 1000;

        /** @return SQLite 路径 */
        public Path getSqlitePath() { return sqlitePath; }
        /** @param sqlitePath SQLite 路径 */
        public void setSqlitePath(Path sqlitePath) { this.sqlitePath = sqlitePath; }
        /** @return 指标保留期 */
        public Duration getRetention() { return retention; }
        /** @param retention 指标保留期 */
        public void setRetention(Duration retention) { this.retention = retention; }
        /** @return 采样间隔 */
        public Duration getSampleInterval() { return sampleInterval; }
        /** @param sampleInterval 采样间隔 */
        public void setSampleInterval(Duration sampleInterval) { this.sampleInterval = sampleInterval; }
        /** @return 最大分页大小 */
        public int getPageLimitMax() { return pageLimitMax; }
        /** @param pageLimitMax 最大分页大小 */
        public void setPageLimitMax(int pageLimitMax) { this.pageLimitMax = pageLimitMax; }
    }

    /** 单个受管池的配置。 */
    public static class Pool {
        private int minimumSize = 1;
        private int maximumSize = 1;
        private int weight = 1;
        private String priority;
        private String reliability;
        private int queueCapacity = 100;
        private String owner;

        public int getMinimumSize() { return minimumSize; }
        public void setMinimumSize(int minimumSize) { this.minimumSize = minimumSize; }
        public int getMaximumSize() { return maximumSize; }
        public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getReliability() { return reliability; }
        public void setReliability(String reliability) { this.reliability = reliability; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        /** @return BEST_EFFORT_DROP 任务的业务所有者 */
        public String getOwner() { return owner; }
        /** @param owner BEST_EFFORT_DROP 任务的业务所有者 */
        public void setOwner(String owner) { this.owner = owner; }
    }
}
