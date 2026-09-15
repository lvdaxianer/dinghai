package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 配置总控 SQLite 控制面和默认安全边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GovernanceServerProperties.class)
@EnableScheduling
public class GovernanceServerConfiguration {
    /**
     * 创建总控 SQLite 存储。
     *
     * @param properties 总控配置
     * @return 控制面存储
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public GovernanceStore governanceStore(GovernanceServerProperties properties) {
        return new GovernanceStore(requireSqlitePath(properties));
    }

    /**
     * 创建实例采集器。
     *
     * @param store 控制面存储
     * @return cursor 采集器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public GovernanceCollector governanceCollector(GovernanceStore store) {
        return new GovernanceCollector(store);
    }

    /**
     * 默认拒绝总控管理 API，部署方必须显式提供授权策略。
     *
     * @return 默认拒绝授权策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public GovernanceAccessPolicy governanceAccessPolicy() { return () -> false; }

    /**
     * 创建子控 HTTP 客户端工厂。
     *
     * @param objectMapper JSON 编解码器
     * @param properties 总控配置
     * @return 实例专属客户端工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public ChildControllerClientFactory childControllerClientFactory(ObjectMapper objectMapper,
                                                                      GovernanceServerProperties properties) {
        validatePollingProperties(properties);
        return instance -> new HttpChildControllerClient(instance.baseUrl(), objectMapper,
                properties.getRequestTimeout(), properties.getApiToken());
    }

    /**
     * 创建总控采集指标发布器。
     *
     * @param registry Micrometer 注册表
     * @return 总控采集指标发布器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public GovernancePollingMetrics governancePollingMetrics(MeterRegistry registry) {
        return new GovernancePollingMetrics(registry);
    }

    /**
     * 创建总控定时轮询器。
     *
     * @param store 控制面存储
     * @param collector cursor 采集器
     * @param clients 子控客户端工厂
     * @param metrics 采集指标发布器
     * @param properties 总控配置
     * @return 单实例轮询器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Bean
    @ConditionalOnMissingBean
    public GovernancePoller governancePoller(GovernanceStore store, GovernanceCollector collector,
                                              ChildControllerClientFactory clients,
                                              GovernancePollingMetrics metrics,
                                              GovernanceServerProperties properties) {
        validatePollingProperties(properties);
        return new GovernancePoller(store, collector, clients, metrics, properties.getPageLimit());
    }

    /**
     * 校验部署配置提供了 SQLite 持久化路径。
     *
     * @param properties 总控配置
     * @return 已校验的 SQLite 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private java.nio.file.Path requireSqlitePath(GovernanceServerProperties properties) {
        if (properties.getSqlitePath() == null) {
            throw new IllegalArgumentException("dinghai.thread-pool.governance.sqlite-path is required");
        }
        return properties.getSqlitePath();
    }

    /** 验证定时采集所需的正数配置，避免生产环境无限等待或无界拉取。 */
    private void validatePollingProperties(GovernanceServerProperties properties) {
        if (properties.getPollInterval() == null || properties.getPollInterval().isNegative()
                || properties.getPollInterval().isZero() || properties.getRequestTimeout() == null
                || properties.getRequestTimeout().isNegative() || properties.getRequestTimeout().isZero()
                || properties.getPageLimit() < 1) {
            throw new IllegalArgumentException("invalid thread-pool governance polling configuration");
        }
    }
}
