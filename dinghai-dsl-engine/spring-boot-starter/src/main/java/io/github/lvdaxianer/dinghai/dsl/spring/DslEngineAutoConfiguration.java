package io.github.lvdaxianer.dinghai.dsl.spring;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalDslAdapter;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.DslAdapter;
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
    public DslAdapter relationalDslAdapter(ObjectProvider<RelationalSchemaPolicy> policies) {
        RelationalSchemaPolicy policy = policies.getIfAvailable();
        if (policy == null) {
            throw new DslBootstrapException(
                    "relational-query is enabled but no RelationalSchemaPolicy bean is configured");
        }
        return new RelationalDslAdapter(policy);
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
