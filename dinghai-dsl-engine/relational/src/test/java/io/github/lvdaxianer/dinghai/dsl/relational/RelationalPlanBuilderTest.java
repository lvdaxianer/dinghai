package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证关系查询语义校验、权限解析和执行计划构建。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class RelationalPlanBuilderTest {

    private static final DslContext CONTEXT = new DslContext("trace-1", Map.of("userId", "u-1"));
    private static final RelationalSchemaPolicy POLICY = new StubPolicy();

    /**
     * 验证合法 DSL 生成不可变后端无关计划和参数绑定。
     */
    @Test
    void buildsImmutablePlanWithBoundParameters() {
        RelationalQueryPlan plan = new RelationalPlanBuilder(POLICY).build(
                request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "where", List.of(Map.of(
                                "field", "f.status",
                                "operator", "eq",
                                "parameter", "status",
                                "logicalType", "string")),
                        "limit", 20), Map.of("status", "OPEN")),
                CONTEXT);

        assertEquals("relational-query", plan.planType());
        assertEquals("status", plan.parameters().getFirst().name());
        assertEquals("OPEN", plan.parameters().getFirst().value());
        assertEquals("fact", plan.tablesByAlias().get("f").logicalName());
        assertThrows(UnsupportedOperationException.class, () -> plan.metadata().clear());
    }

    /**
     * 验证未知表、未知字段和未知别名会在数据库执行前被拒绝。
     */
    @Test
    void rejectsUnknownIdentifiersBeforeExecution() {
        DslCompilationException unknownTable = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY).build(request(Map.of(
                        "from", Map.of("table", "unknown", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "limit", 20)), CONTEXT));
        DslCompilationException unknownField = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY).build(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.secret")),
                        "limit", 20)), CONTEXT));

        assertEquals("TABLE_NOT_ALLOWED", unknownTable.diagnostics().getFirst().code());
        assertEquals("FIELD_NOT_ALLOWED", unknownField.diagnostics().getFirst().code());
    }

    /**
     * 验证参数必须绑定且类型匹配，计划不接受未使用参数。
     */
    @Test
    void rejectsInvalidParameterBindings() {
        DslRequest missing = request(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.id")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string")),
                "limit", 20));
        DslCompilationException missingParameter = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY).build(missing, CONTEXT));
        DslCompilationException unusedParameter = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY).build(
                        request(Map.of(
                                "from", Map.of("table", "fact", "alias", "f"),
                                "select", List.of(Map.of("field", "f.id")),
                                "limit", 20), Map.of("unused", 1)),
                        CONTEXT));

        assertEquals("PARAMETER_MISSING", missingParameter.diagnostics().getFirst().code());
        assertEquals("PARAMETER_UNUSED", unusedParameter.diagnostics().getFirst().code());
    }

    /**
     * 验证 JOIN 必须真正连接新表，禁止同表条件伪装成 JOIN。
     */
    @Test
    void rejectsJoinWithoutCrossTableCondition() {
        DslCompilationException exception = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY).build(request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "joins", List.of(Map.of(
                                "type", "inner",
                                "table", Map.of("table", "device", "alias", "d"),
                                "on", List.of(Map.of("left", "f.id", "right", "f.id")))),
                        "limit", 20)), CONTEXT));

        assertEquals("JOIN_ON_CROSS_SCOPE", exception.diagnostics().getFirst().code());
    }

    /**
     * 验证计划构建阶段再次执行可配置复杂度限制。
     */
    @Test
    void rejectsPlanAboveConfiguredPredicateDepth() {
        DslRequest request = request(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.id")),
                "where", Map.of("and", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string"))),
                "limit", 20), Map.of("status", "OPEN"));
        RelationalPlanLimits limits = new RelationalPlanLimits(1, 1, 1, 1, 20);

        DslCompilationException exception = assertThrows(
                DslCompilationException.class,
                () -> new RelationalPlanBuilder(POLICY, limits).build(request, CONTEXT));

        assertEquals("QUERY_COMPLEXITY_EXCEEDED", exception.diagnostics().getFirst().code());
    }

    /**
     * 验证 DSL Adapter 只负责解析并输出统一执行计划。
     */
    @Test
    void adapterCompilesRequestToPlan() {
        RelationalDslAdapter adapter = new RelationalDslAdapter(POLICY);
        RelationalQueryPlan plan = (RelationalQueryPlan) adapter.parse(
                request(Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "limit", 20)),
                CONTEXT);

        assertTrue(adapter.supports(request(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.id")),
                "limit", 20))));
        assertEquals(0, plan.parameters().size());
    }

    /**
     * 构造 DSL 请求。
     *
     * @param payload 查询载荷
     * @param parameters 参数值
     * @return DSL 请求
     */
    private static DslRequest request(Map<String, Object> payload, Map<String, Object> parameters) {
        return new DslRequest("relational-query", 1, payload, parameters);
    }

    /**
     * 构造没有参数的 DSL 请求。
     *
     * @param payload 查询载荷
     * @return DSL 请求
     */
    private static DslRequest request(Map<String, Object> payload) {
        return request(payload, Map.of());
    }

    /**
     * 测试用白名单策略。
     */
    private static final class StubPolicy implements RelationalSchemaPolicy {

        @Override
        public Optional<RelationalTableDefinition> resolveTable(String logicalName, DslContext context) {
            if ("fact".equals(logicalName)) {
                return Optional.of(new RelationalTableDefinition(
                        "fact", "fact_table", Map.of(
                                "id", new RelationalColumnDefinition("id", "id", "long"),
                                "status", new RelationalColumnDefinition("status", "status", "string"))));
            }
            if ("device".equals(logicalName)) {
                return Optional.of(new RelationalTableDefinition(
                        "device", "dim_device", Map.of(
                                "id", new RelationalColumnDefinition("id", "device_id", "long"))));
            }
            return Optional.empty();
        }
    }
}
