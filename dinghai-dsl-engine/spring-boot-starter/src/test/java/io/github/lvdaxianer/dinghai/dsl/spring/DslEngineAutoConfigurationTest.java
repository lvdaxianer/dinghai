package io.github.lvdaxianer.dinghai.dsl.spring;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

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
            assertThat(context.getBean(DslAdapterRegistry.class).registeredTypes())
                    .containsExactly("relational-query");
            assertThat(context.getBean(DataAdapterRegistry.class).registeredTypes())
                    .containsExactly("mysql");
        });
    }
}
