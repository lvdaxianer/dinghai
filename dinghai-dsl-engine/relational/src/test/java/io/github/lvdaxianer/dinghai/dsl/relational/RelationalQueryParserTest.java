package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.AggregateFunction;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.AggregateSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.FieldSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Join;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.OrderDirection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 relational-query v1 的严格结构解析和类型化 AST。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class RelationalQueryParserTest {

    /**
     * 验证单表投影、参数条件、分组、排序和限制可以解析。
     */
    @Test
    void parsesSingleTableQueryIntoTypedAst() {
        RelationalQueryAst ast = new RelationalQueryParser().parse(request(Map.of(
                "from", Map.of("table", "fact_wireless_outage_timeline", "alias", "f"),
                "select", List.of(Map.of("field", "f.event_date")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string")),
                "groupBy", List.of("f.event_date"),
                "orderBy", List.of(Map.of("field", "f.event_date", "direction", "desc")),
                "limit", 100)));

        assertEquals("fact_wireless_outage_timeline", ast.from().table());
        assertEquals("f", ast.from().alias());
        assertEquals(new RelationalField("f", "event_date"), ((FieldSelection) ast.select().getFirst()).field());
        assertEquals("status", ((ComparisonPredicate) ast.where().getFirst()).parameter().name());
        assertEquals(OrderDirection.DESC, ast.orderBy().getFirst().direction());
        assertEquals(100, ast.limit());
    }

    /**
     * 验证 INNER JOIN 的 ON 条件和聚合字段可以解析。
     */
    @Test
    void parsesJoinAndAggregateIntoTypedAst() {
        Map<String, Object> payload = Map.of(
                "from", Map.of("table", "fact_outage", "alias", "f"),
                "select", List.of(
                        Map.of("field", "f.event_date"),
                        Map.of("aggregate", "count", "field", "d.id", "alias", "total")),
                "joins", List.of(Map.of(
                        "type", "inner",
                        "table", Map.of("table", "dim_device", "alias", "d"),
                        "on", List.of(Map.of("left", "f.device_id", "right", "d.id")))),
                "limit", 20);
        RelationalQueryAst ast = new RelationalQueryParser().parse(request(payload));

        Join join = ast.joins().getFirst();
        AggregateSelection aggregate = (AggregateSelection) ast.select().get(1);
        assertEquals("d", join.table().alias());
        assertEquals("f", join.on().getFirst().left().qualifier());
        assertEquals(AggregateFunction.COUNT, aggregate.function());
        assertEquals("total", aggregate.alias());
    }

    /**
     * 验证未知字段和错误类型返回带路径的稳定诊断。
     */
    @Test
    void rejectsUnknownFieldsAndWrongTypesWithDiagnostics() {
        DslCompilationException unknown = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "sql", "select * from fact"))));
        DslCompilationException wrongType = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", "f.id"))));

        assertEquals("UNKNOWN_FIELD", unknown.diagnostics().getFirst().code());
        assertTrue(unknown.diagnostics().getFirst().path().contains("$.sql"));
        assertEquals("INVALID_TYPE", wrongType.diagnostics().getFirst().code());
    }

    /**
     * 验证缺失必填结构、笛卡尔 JOIN 和过深输入会被拒绝。
     */
    @Test
    void rejectsMissingRequiredShapeAndDepthOverflow() {
        DslCompilationException missing = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f")))));
        DslCompilationException joinWithoutOn = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "joins", List.of(Map.of(
                                "type", "left",
                                "table", Map.of("table", "dim", "alias", "d")))))));

        assertEquals("MISSING_FIELD", missing.diagnostics().getFirst().code());
        assertEquals("JOIN_ON_REQUIRED", joinWithoutOn.diagnostics().getFirst().code());
    }

    /**
     * 验证嵌套条件超过结构深度后会被拒绝。
     */
    @Test
    void rejectsDepthOverflow() {
        Map<String, Object> nested = Map.of(
                "field", "f.id",
                "operator", "eq",
                "parameter", "id",
                "logicalType", "long");
        for (int index = 0; index < 20; index++) {
            nested = Map.of("and", List.of(nested));
        }
        Map<String, Object> deepPredicate = nested;
        DslCompilationException exception = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "where", deepPredicate,
                        "limit", 10))));

        assertEquals("INPUT_COMPLEXITY_EXCEEDED", exception.diagnostics().getFirst().code());
    }

    /**
     * 验证表名中的 SQL 片段不会被当作标识符接受。
     */
    @Test
    void rejectsRawSqlInTableIdentifier() {
        DslCompilationException exception = assertThrows(
                DslCompilationException.class,
                () -> new RelationalQueryParser().parse(request(Map.of(
                        "from", Map.of("table", "fact;drop", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "limit", 10))));

        assertEquals("INVALID_IDENTIFIER", exception.diagnostics().getFirst().code());
    }

    /**
     * 构造统一 DSL 请求。
     *
     * @param payload 查询载荷
     * @return DSL 请求
     */
    private static DslRequest request(Map<String, Object> payload) {
        return new DslRequest("relational-query", 1, payload, Map.of());
    }
}
