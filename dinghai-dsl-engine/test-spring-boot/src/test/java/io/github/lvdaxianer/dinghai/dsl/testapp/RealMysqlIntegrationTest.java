package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryExecutionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL JDBC 集成测试。Doris 通过同一个 MySQL adapter 复用同等断言。
 */
@EnabledIfSystemProperty(named = "dinghai.integration.enabled", matches = "true")
@SpringBootTest(properties = "dinghai.data-query.default-data-adapter=mysql")
class RealMysqlIntegrationTest {

    @Autowired
    private QueryExecutionEngine engine;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("DINGHAI_IT_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:13306/dinghai_test"));
        registry.add("spring.datasource.username", () -> env("DINGHAI_IT_DB_USERNAME", "dinghai_test"));
        registry.add("spring.datasource.password", () -> env("DINGHAI_IT_DB_PASSWORD", "dinghai_local_only"));
    }

    @Test
    void executesJoinAndAppliesActiveDomainScopeAgainstRealMysql() {
        QueryResult result = engine.execute(
                RealIntegrationRequest.activeOpenOutages(),
                new DslContext("real-mysql", Map.of()),
                new QueryContext("real-mysql", "mysql", Map.of(
                        "userId", "test-user", "domainId", "test-domain")));

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst()).containsExactly(
                java.time.LocalDate.of(2026, 11, 13), "asset-101");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
