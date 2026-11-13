package io.github.lvdaxianer.dinghai.dsl.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 DSL Engine 核心模型的不可变契约。
 *
 * @author lvdaxianer@yeah.net
 */
class CoreContractModelTest {

    /**
     * 验证请求会复制并冻结嵌套 payload。
     */
    @Test
    void requestCopiesAndFreezesNestedPayload() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("field", "event_date");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("select", nested);

        DslRequest request = new DslRequest("relational-query", 1, payload, Map.of());
        nested.put("field", "changed");

        assertEquals("event_date", ((Map<?, ?>) request.payload().get("select")).get("field"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<?, ?>) request.payload().get("select")).clear());
    }

    /**
     * 验证通用执行计划只暴露不可变元数据。
     */
    @Test
    void executionPlanMetadataIsImmutable() {
        ExecutionPlan plan = new GenericExecutionPlan("relational", 1, Map.of("source", "default"));

        assertEquals("relational", plan.planType());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.metadata().put("source", "other"));
    }

    /**
     * 验证 DSL null 值和数据库 null 结果可以保留。
     */
    @Test
    void nullValuesRemainRepresentable() {
        DslRequest request = new DslRequest("relational-query", 1,
                Map.of("filter", java.util.Collections.singletonMap("value", null)), Map.of());
        QueryResult result = new QueryResult(
                java.util.List.of(new QueryColumn("value", "string")),
                java.util.List.of(java.util.Collections.singletonList(null)), 1, false);

        assertNull(((Map<?, ?>) request.payload().get("filter")).get("value"));
        assertNull(result.rows().get(0).get(0));
    }
}
