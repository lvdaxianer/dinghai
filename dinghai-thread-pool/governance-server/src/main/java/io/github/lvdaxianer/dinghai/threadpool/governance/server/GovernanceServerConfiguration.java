package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置总控 SQLite 控制面和默认安全边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GovernanceServerProperties.class)
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
}
