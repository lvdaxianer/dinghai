package io.github.lvdaxianer.dinghai.dsl.spring;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalDslAdapter;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalPlanLimits;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.DslAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.NoopQueryTelemetry;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanPolicy;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryAuditSink;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryExecutionEngine;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryTelemetry;
import io.github.lvdaxianer.dinghai.dsl.spi.RuntimeExecutionLimits;
import io.github.lvdaxianer.dinghai.dsl.spi.SafeQueryLogger;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.AdapterRegistryException;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * DSL 引擎公共注册表自动装配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
@AutoConfiguration
@EnableConfigurationProperties(DslEngineProperties.class)
public class DslEngineAutoConfiguration {

    /**
     * 装配统一 DSL 执行引擎。
     *
     * @param dslAdapters DSL 注册表
     * @param dataAdapters 数据适配器注册表
     * @param properties 启动配置
     * @param policies 宿主计划策略
     * @param auditSinks 审计接收器
     * @param telemetry 指标接收器
     * @return DSL 执行引擎
     */
    @Bean(destroyMethod = "close")
    public QueryExecutionEngine queryExecutionEngine(
            DslAdapterRegistry dslAdapters,
            DataAdapterRegistry dataAdapters,
            DslEngineProperties properties,
            ObjectProvider<PlanPolicy> policies,
            ObjectProvider<QueryAuditSink> auditSinks,
            ObjectProvider<QueryTelemetry> telemetry) {
        PlanPolicy policy = policies.getIfAvailable();
        if (policy == null) {
            throw new DslBootstrapException(
                    "DSL engine requires a server-managed PlanPolicy for data authorization");
        }
        QueryAuditSink auditSink = auditSinks.getIfAvailable(
                () -> new SafeQueryLogger(System.getLogger("io.github.lvdaxianer.dinghai.dsl")));
        QueryTelemetry metrics = telemetry.getIfAvailable(() -> NoopQueryTelemetry.INSTANCE);
        RuntimeExecutionLimits limits = new RuntimeExecutionLimits(
                properties.getMaxRequestBytes(),
                properties.getMaxConcurrency(),
                properties.getTimeout(),
                properties.getMaxPlanNodes(),
                properties.getMaxPlanDepth(),
                properties.getMaxJoins(),
                properties.getMaxRows());
        return new QueryExecutionEngine(
                dslAdapters, dataAdapters, properties.getDefaultDataAdapter(), policy, auditSink, metrics, limits);
    }

    /**
     * 装配内置关系查询 DSL 适配器。
     *
     * @param policies 企业或应用提供的 schema 白名单策略
     * @return 关系 DSL 适配器
     */
    @Bean(name = "dinghaiRelationalDslAdapter")
    @ConditionalOnProperty(
            prefix = "dinghai.data-query.dsl-adapters",
            name = "relational-query",
            havingValue = "enabled",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "dinghaiRelationalDslAdapter")
    public DslAdapter relationalDslAdapter(
            ObjectProvider<RelationalSchemaPolicy> policies, DslEngineProperties properties) {
        RelationalSchemaPolicy policy = policies.getIfAvailable();
        if (policy == null) {
            throw new DslBootstrapException(
                    "relational-query is enabled but no RelationalSchemaPolicy bean is configured");
        }
        return new RelationalDslAdapter(policy, new RelationalPlanLimits(
                properties.getMaxJoins(), 64, 128, properties.getMaxPlanDepth(), properties.getMaxRows()));
    }

    /**
     * 构建并冻结 DSL 注册表。
     *
     * @param adapters Spring 已发现的 DSL 适配器
     * @param properties 启动配置
     * @return 冻结注册表
     */
    @Bean
    public DslAdapterRegistry dslAdapterRegistry(
            List<DslAdapter> adapters, DslEngineProperties properties) {
        DslAdapterRegistry registry = new DslAdapterRegistry(adapters);
        validateDslConfiguration(registry, properties.getDslAdapters());
        return registry;
    }

    /**
     * 构建并冻结数据适配器注册表，同时校验默认后端已装配。
     *
     * @param adapters Spring 已发现的数据适配器
     * @param properties 启动配置
     * @return 冻结注册表
     */
    @Bean
    public DataAdapterRegistry dataAdapterRegistry(
            List<DataAdapter> adapters, DslEngineProperties properties) {
        DataAdapterRegistry registry = new DataAdapterRegistry(adapters);
        try {
            registry.resolve(requireText(properties.getDefaultDataAdapter(), "default-data-adapter"));
        } catch (AdapterRegistryException exception) {
            throw new DslBootstrapException(
                    "Configured default data adapter is not registered: " + properties.getDefaultDataAdapter());
        }
        return registry;
    }

    /**
     * 校验所有启用的 DSL 都有精确适配器。
     *
     * @param registry DSL 注册表
     * @param configured 配置项
     */
    private static void validateDslConfiguration(
            DslAdapterRegistry registry, Map<String, String> configured) {
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            String type = requireText(entry.getKey(), "dsl-adapter.type");
            String state = requireText(entry.getValue(), "dsl-adapter.state");
            if (!"enabled".equals(state) && !"disabled".equals(state)) {
                throw new DslBootstrapException("DSL adapter state must be enabled or disabled: " + type);
            }
            if ("enabled".equals(state) && !registry.registeredTypes().contains(type)) {
                throw new DslBootstrapException("Configured DSL adapter is not registered: " + type);
            }
        }
    }

    /**
     * 校验配置文本。
     *
     * @param value 配置文本
     * @param name 配置名
     * @return 非空文本
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DslBootstrapException(name + " must not be blank");
        }
        return value.trim();
    }
}
