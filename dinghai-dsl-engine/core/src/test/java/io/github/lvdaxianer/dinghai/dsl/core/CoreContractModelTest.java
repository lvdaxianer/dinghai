package io.github.lvdaxianer.dinghai.dsl.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 验证查询结果可以按列名生成可读记录，并保留空值字段。
     */
    @Test
    void recordsMapRowsByColumnNameAndPreserveNulls() {
        QueryResult result = new QueryResult(
                List.of(
                        new QueryColumn("event_date", "date"),
                        new QueryColumn("status", "string"),
                        new QueryColumn("severity", "integer")),
                List.of(
                        java.util.Arrays.asList("2026-11-13", null, 2),
                        java.util.Arrays.asList("2026-11-14", "CLOSED", null)),
                2, false);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("event_date", "2026-11-13");
        first.put("status", null);
        first.put("severity", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("event_date", "2026-11-14");
        second.put("status", "CLOSED");
        second.put("severity", null);

        assertEquals(List.of(first, second),
                result.records());
    }

    /**
     * 验证重复输出列名不会在可读记录中静默覆盖值。
     */
    @Test
    void recordsDisambiguateDuplicateColumnNames() {
        QueryResult result = new QueryResult(
                List.of(new QueryColumn("value", "string"), new QueryColumn("value", "string")),
                List.of(List.of("first", "second")), 1, false);

        assertEquals(Map.of("value", "first", "value#2", "second"), result.records().get(0));
    }

    /**
     * 验证列和值数量不一致时不会产生错位结果。
     */
    @Test
    void rejectsRowsWithDifferentWidthThanColumns() {
        assertThrows(IllegalArgumentException.class, () -> new QueryResult(
                List.of(new QueryColumn("value", "string")),
                List.of(List.of("first", "unexpected")), 1, false));
    }
}
