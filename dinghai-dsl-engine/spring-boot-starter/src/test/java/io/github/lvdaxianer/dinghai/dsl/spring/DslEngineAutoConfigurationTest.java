package io.github.lvdaxianer.dinghai.dsl.spring;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryExecutionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 验证 DSL 引擎启动期能够根据配置完成注册表装配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class DslEngineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DslEngineAutoConfiguration.class,
                    MysqlDataAdapterAutoConfiguration.class,
                    PostgresqlDataAdapterAutoConfiguration.class))
            .withPropertyValues(
                    "dinghai.data-query.default-data-adapter=mysql",
                    "dinghai.data-query.dsl-adapters.relational-query=enabled")
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(RelationalSchemaPolicy.class, () -> (logicalName, context) -> java.util.Optional.empty());

    /**
     * 验证有效配置可以装配 DSL 和数据适配器注册表。
     */
    @Test
    void loadsFrozenRegistriesForConfiguredMysqlBackend() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DslAdapterRegistry.class);
            assertThat(context).hasSingleBean(DataAdapterRegistry.class);
            assertThat(context).hasSingleBean(QueryExecutionEngine.class);
            assertThat(context.getBean(DslAdapterRegistry.class).registeredTypes())
                    .containsExactly("relational-query");
            assertThat(context.getBean(DataAdapterRegistry.class).registeredTypes())
                    .containsExactly("mysql");
        });
    }

    /**
     * 验证默认后端没有实现或注册时启动失败。
     */
    @Test
    void rejectsUnknownDefaultBackendBeforeReady() {
        contextRunner
                .withPropertyValues("dinghai.data-query.default-data-adapter=oracle")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("Configured default data adapter is not registered"));
    }

    /**
     * 验证缺少服务端 DataSource 时启动失败。
     */
    @Test
    void rejectsMissingServerManagedDataSourceBeforeReady() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DslEngineAutoConfiguration.class,
                        MysqlDataAdapterAutoConfiguration.class,
                        PostgresqlDataAdapterAutoConfiguration.class))
                .withPropertyValues("dinghai.data-query.default-data-adapter=mysql")
                .withBean(RelationalSchemaPolicy.class, () -> (logicalName, context) -> java.util.Optional.empty())
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("requires a server-managed DataSource"));
    }

    /**
     * 验证启用的 DSL 没有对应适配器时启动失败。
     */
    @Test
    void rejectsEnabledDslWithoutRegisteredAdapterBeforeReady() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DslEngineAutoConfiguration.class,
                        MysqlDataAdapterAutoConfiguration.class,
                        PostgresqlDataAdapterAutoConfiguration.class))
                .withPropertyValues(
                        "dinghai.data-query.default-data-adapter=mysql",
                        "dinghai.data-query.dsl-adapters.relational-query=disabled",
                        "dinghai.data-query.dsl-adapters.future-query=enabled")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("Configured DSL adapter is not registered: future-query"));
    }

    /**
     * 验证 DSL 配置值不是 enabled/disabled 时启动失败。
     */
    @Test
    void rejectsInvalidDslAdapterStateBeforeReady() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DslEngineAutoConfiguration.class,
                        MysqlDataAdapterAutoConfiguration.class,
                        PostgresqlDataAdapterAutoConfiguration.class))
                .withPropertyValues(
                        "dinghai.data-query.default-data-adapter=mysql",
                        "dinghai.data-query.dsl-adapters.relational-query=maybe")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("DSL adapter state must be enabled or disabled"));
    }

    /**
     * 验证重复数据适配器和能力类型不一致不会被静默接受。
     */
    @Test
    void rejectsDuplicateOrMismatchedDataAdapterDeclaration() {
        DataAdapter first = adapter("mysql", "mysql");
        DataAdapter duplicate = adapter("mysql", "mysql");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DslEngineAutoConfiguration.class))
                .withPropertyValues(
                        "dinghai.data-query.default-data-adapter=mysql",
                        "dinghai.data-query.dsl-adapters.relational-query=disabled")
                .withBean("firstDataAdapter", DataAdapter.class, () -> first)
                .withBean("duplicateDataAdapter", DataAdapter.class, () -> duplicate)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("Duplicate data adapter registration"));

        DataAdapter mismatched = adapter("mysql", "postgresql");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DslEngineAutoConfiguration.class))
                .withPropertyValues(
                        "dinghai.data-query.default-data-adapter=mysql",
                        "dinghai.data-query.dsl-adapters.relational-query=disabled")
                .withBean("mismatchedDataAdapter", DataAdapter.class, () -> mismatched)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("capability type does not match"));
    }

    /**
     * 构造最小数据适配器声明。
     *
     * @param type 适配器类型
     * @param capabilityType 能力类型
     * @return 数据适配器
     */
    private static DataAdapter adapter(String type, String capabilityType) {
        return new DataAdapter() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Set<String> supportedPlanTypes() {
                return Set.of("relational-query");
            }

            @Override
            public BackendCapabilities capabilities() {
                return new BackendCapabilities(capabilityType, Set.of("relational-query"));
            }

            @Override
            public QueryResult execute(ExecutionPlan plan, QueryContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
