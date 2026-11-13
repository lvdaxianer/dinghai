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
 * 真实 Doris MySQL 协议集成测试，明确复用 MySQLDataAdapter。
 */
@EnabledIfSystemProperty(named = "dinghai.integration.enabled", matches = "true")
@SpringBootTest(properties = "dinghai.data-query.default-data-adapter=mysql")
class RealDorisIntegrationTest {

    @Autowired
    private QueryExecutionEngine engine;

    @DynamicPropertySource
    static void dorisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("DINGHAI_IT_DORIS_URL",
                "jdbc:mysql://127.0.0.1:19030/dinghai_test"));
        registry.add("spring.datasource.username", () -> env("DINGHAI_IT_DORIS_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> env("DINGHAI_IT_DORIS_PASSWORD", ""));
    }

    @Test
    void executesThroughMysqlAdapterAgainstRealDoris() {
        QueryResult result = engine.execute(
                RealIntegrationRequest.activeOpenOutages(),
                new DslContext("real-doris", Map.of()),
                new QueryContext("real-doris", "doris-mysql", Map.of(
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
