package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.task.TaskDecorator;

/**
 * Dinghai 线程池治理自动装配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "dinghai.thread-pool", name = "enabled", havingValue = "true")
public class ThreadPoolAutoConfiguration {

    /** 创建每个应用实例唯一的受管线程池治理器。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ThreadPoolGovernor threadPoolGovernor(ThreadPoolProperties properties) {
        ThreadPoolGovernor governor = new ThreadPoolGovernor(properties.getManagedWorkerBudget());
        new ThreadPoolRegistrar().register(governor, properties.getPools());
        return governor;
    }

    /** 创建默认的 MDC 任务装饰器。 */
    @Bean
    @ConditionalOnMissingBean
    public TaskDecorator dinghaiThreadPoolTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /** 创建子控 SQLite 指标库。 */
    @Bean
    @ConditionalOnMissingBean
    public SqliteMetricStore threadPoolMetricStore(ThreadPoolProperties properties) {
        return new SqliteMetricStore(properties.getMetrics().getSqlitePath(),
                properties.getMetrics().getPageLimitMax());
    }

    /** 注册治理器运行指标采样器。 */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolMetricsSampler threadPoolMetricsSampler(ThreadPoolGovernor governor, SqliteMetricStore store,
                                                              ThreadPoolProperties properties) {
        return new ThreadPoolMetricsSampler(governor, store, properties.getMetrics().getRetention());
    }

    /** 创建默认关闭的指标采集端点。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dinghai.thread-pool", name = "endpoint-enabled", havingValue = "true")
    public ThreadPoolMetricController threadPoolMetricController(SqliteMetricStore store, ThreadPoolGovernor governor,
                                                                  ThreadPoolMetricsAccessPolicy accessPolicy) {
        return new ThreadPoolMetricController(store, governor, accessPolicy);
    }

    /** 注册子控端点的协议异常映射器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dinghai.thread-pool", name = "endpoint-enabled", havingValue = "true")
    public ThreadPoolEndpointExceptionHandler threadPoolEndpointExceptionHandler() {
        return new ThreadPoolEndpointExceptionHandler();
    }

    /** 注册默认拒绝访问的指标端点授权策略。 */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolMetricsAccessPolicy threadPoolMetricsAccessPolicy() {
        return () -> false;
    }
}
