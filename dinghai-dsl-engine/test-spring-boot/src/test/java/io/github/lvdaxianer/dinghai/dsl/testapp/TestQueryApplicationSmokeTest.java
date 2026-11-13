package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.spi.QueryExecutionEngine;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.PlanExtensionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证测试应用可在无生产秘密默认值下完成 Spring Boot 启动装配。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unused",
        "spring.datasource.username=test",
        "spring.datasource.password=",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
class TestQueryApplicationSmokeTest {

    @Autowired
    private QueryExecutionEngine engine;

    @Autowired
    private PlanExtensionRegistry extensions;

    @Test
    void startsWithServerManagedEngineAndExtensionRegistry() {
        assertThat(engine).isNotNull();
        assertThat(extensions.registeredIds()).containsExactly("test-active-domain-scope");
    }
}
