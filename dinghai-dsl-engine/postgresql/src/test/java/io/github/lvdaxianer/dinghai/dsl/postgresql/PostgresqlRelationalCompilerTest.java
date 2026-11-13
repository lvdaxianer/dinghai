package io.github.lvdaxianer.dinghai.dsl.postgresql;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalColumnDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalPlanBuilder;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryPlan;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalTableDefinition;
import io.github.lvdaxianer.dinghai.dsl.spi.CompiledCommand;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCompilationException;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 PostgreSQL 独立编译器的方言和参数绑定。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class PostgresqlRelationalCompilerTest {

    private static final DslContext DSL_CONTEXT = new DslContext("trace-1", Map.of());
    private static final QueryContext QUERY_CONTEXT = new QueryContext("trace-1", "postgresql", Map.of());

    /**
     * 验证 PostgreSQL 使用双引号标识符和参数化条件。
     */
    @Test
    void compilesPostgresqlQuotedIdentifiers() {
        RelationalQueryPlan plan = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.status")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string")),
                "limit", 100), Map.of("status", "OPEN"));

        CompiledCommand command = new PostgresqlRelationalCompiler().compile(plan, QUERY_CONTEXT);
        String sql = (String) command.commandBody();

        assertEquals("postgresql", new PostgresqlRelationalCompiler().backendType());
        assertTrue(sql.startsWith("SELECT \"f\".\"status\" AS \"f_status\" FROM \"fact_table\" AS \"f\""));
        assertTrue(sql.contains("WHERE \"f\".\"status\" = ? LIMIT 100"));
        assertEquals("OPEN", command.parameters().getFirst().value());
    }

    /**
     * 验证 PostgreSQL JOIN 和聚合生成独立目标 SQL。
     */
    @Test
    void compilesPostgresqlJoinAndAggregate() {
        RelationalQueryPlan plan = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("aggregate", "count", "field", "d.id", "alias", "total")),
                "joins", List.of(Map.of(
                        "type", "inner",
                        "table", Map.of("table", "device", "alias", "d"),
                        "on", List.of(Map.of("left", "f.device_id", "right", "d.id")))),
                "limit", 10), Map.of());

        String sql = (String) new PostgresqlRelationalCompiler().compile(plan, QUERY_CONTEXT).commandBody();

        assertTrue(sql.contains("INNER JOIN \"dim_device\" AS \"d\" ON \"f\".\"device_id\" = \"d\".\"device_id\""));
        assertTrue(sql.contains("COUNT(\"d\".\"device_id\") AS \"total\""));
    }

    /**
     * 验证 PostgreSQL 编译器也能把扩展过滤条件安全地放入 JOIN ON。
     */
    @Test
    void compilesParameterizedJoinOnPredicate() {
        RelationalQueryPlan base = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.status")),
                "joins", List.of(Map.of(
                        "type", "left",
                        "table", Map.of("table", "device", "alias", "d"),
                        "on", List.of(Map.of("left", "f.device_id", "right", "d.id")))),
                "limit", 10), Map.of());
        RelationalQueryAst.Join join = base.query().joins().getFirst();
        RelationalQueryAst.Join extendedJoin = new RelationalQueryAst.Join(
                join.type(),
                join.table(),
                join.on(),
                List.of(new RelationalQueryAst.ComparisonPredicate(
                        new RelationalQueryAst.RelationalField("d", "deleted"),
                        RelationalQueryAst.ComparisonOperator.EQ,
                        new RelationalQueryAst.ParameterReference("__ext_deleted", "integer"))));
        RelationalQueryAst extendedQuery = new RelationalQueryAst(
                base.query().from(),
                base.query().select(),
                List.of(extendedJoin),
                base.query().where(),
                base.query().groupBy(),
                base.query().orderBy(),
                base.query().limit());
        RelationalQueryPlan extended = new RelationalQueryPlan(
                extendedQuery,
                base.tablesByAlias(),
                List.of(new ParameterBinding("__ext_deleted", "integer", 0)),
                base.predicateDepth(),
                base.metadata());

        CompiledCommand command = new PostgresqlRelationalCompiler().compile(extended, QUERY_CONTEXT);
        String sql = (String) command.commandBody();

        assertTrue(sql.contains("LEFT JOIN \"dim_device\" AS \"d\" ON \"f\".\"device_id\" = \"d\".\"device_id\""));
        assertTrue(sql.contains("AND \"d\".\"deleted\" = ?"));
        assertEquals(0, command.parameters().getFirst().value());
    }

    /**
     * 验证 PostgreSQL 能力声明与编译器类型严格一致。
     */
    @Test
    void declaresPostgresqlCapabilitiesWithoutMysqlFallback() {
        BackendCapabilities capabilities = PostgresqlCapabilityProfile.common();

        assertEquals("postgresql", capabilities.backendType());
        assertTrue(capabilities.features().contains("relational-query"));
        assertTrue(capabilities.features().contains("parameterized-read"));
        assertEquals("postgresql", new PostgresqlRelationalCompiler().backendType());
    }

    /**
     * 验证 PostgreSQL 不会把未知计划类型降级为 SQL。
     */
    @Test
    void rejectsUnsupportedPlanTypeWithoutFallback() {
        BackendCompilationException exception = assertThrows(BackendCompilationException.class,
                () -> new PostgresqlRelationalCompiler().compile(
                        new GenericExecutionPlan("unknown", 1, Map.of()), QUERY_CONTEXT));

        assertEquals("PLAN_TYPE_NOT_SUPPORTED", exception.code());
    }

    /**
     * 构造关系查询计划。
     *
     * @param payload 查询载荷
     * @param parameters 参数值
     * @return 关系查询计划
     */
    private static RelationalQueryPlan plan(Map<String, Object> payload, Map<String, Object> parameters) {
        RelationalSchemaPolicy policy = (logicalName, context) -> switch (logicalName) {
            case "fact" -> Optional.of(new RelationalTableDefinition(
                    "fact", "fact_table", Map.of(
                            "status", new RelationalColumnDefinition("status", "status", "string"),
                            "device_id", new RelationalColumnDefinition("device_id", "device_id", "long"))));
            case "device" -> Optional.of(new RelationalTableDefinition(
                    "device", "dim_device", Map.of(
                        "id", new RelationalColumnDefinition("id", "device_id", "long"),
                        "deleted", new RelationalColumnDefinition("deleted", "deleted", "integer"))));
            default -> Optional.empty();
        };
        return new RelationalPlanBuilder(policy).build(
                new DslRequest("relational-query", 1, payload, parameters), DSL_CONTEXT);
    }
}
