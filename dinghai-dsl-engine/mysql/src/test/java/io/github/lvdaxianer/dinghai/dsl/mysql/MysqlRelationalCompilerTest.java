package io.github.lvdaxianer.dinghai.dsl.mysql;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalColumnDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalTableDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalPlanBuilder;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCompilationException;
import io.github.lvdaxianer.dinghai.dsl.spi.CompiledCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MySQL/Doris 公共能力编译器的 SQL 和参数绑定。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class MysqlRelationalCompilerTest {

    private static final DslContext DSL_CONTEXT = new DslContext("trace-1", Map.of());
    private static final QueryContext QUERY_CONTEXT = new QueryContext("trace-1", "mysql", Map.of());

    /**
     * 验证字段、条件、分组、排序和限制生成参数化 MySQL SQL。
     */
    @Test
    void compilesParameterizedRelationalPlan() {
        RelationalQueryPlan plan = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.status")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string")),
                "groupBy", List.of("f.status"),
                "orderBy", List.of(Map.of("field", "f.status", "direction", "asc")),
                "limit", 100), Map.of("status", "OPEN"));

        CompiledCommand command = new MysqlRelationalCompiler().compile(plan, QUERY_CONTEXT);
        String sql = (String) command.commandBody();

        assertTrue(sql.startsWith("SELECT `f`.`status` AS `f_status` FROM `fact_table` AS `f`"));
        assertTrue(sql.contains("WHERE `f`.`status` = ?"));
        assertTrue(sql.contains("GROUP BY `f`.`status` ORDER BY `f`.`status` ASC LIMIT 100"));
        assertFalse(sql.contains("OPEN"));
        assertEquals("OPEN", command.parameters().getFirst().value());
        assertEquals("f_status", command.outputColumns().getFirst().name());
    }

    /**
     * 验证 JOIN 和聚合使用 MySQL 与 Doris 共同支持的通用语法。
     */
    @Test
    void compilesCommonJoinAndAggregateSyntax() {
        RelationalQueryPlan plan = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("aggregate", "count", "field", "d.id", "alias", "total")),
                "joins", List.of(Map.of(
                        "type", "left",
                        "table", Map.of("table", "device", "alias", "d"),
                        "on", List.of(Map.of("left", "f.device_id", "right", "d.id")))),
                "limit", 10), Map.of());

        String sql = (String) new MysqlRelationalCompiler().compile(plan, QUERY_CONTEXT).commandBody();

        assertTrue(sql.contains("LEFT JOIN `dim_device` AS `d` ON `f`.`device_id` = `d`.`device_id`"));
        assertTrue(sql.contains("COUNT(`d`.`device_id`) AS `total`"));
    }

    /**
     * 验证 IN 参数按元素展开为绑定占位符，不拼接用户值。
     */
    @Test
    void expandsInParameterAsBindings() {
        RelationalQueryPlan plan = plan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.status")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "in",
                        "parameter", "statuses",
                        "logicalType", "string")),
                "limit", 10), Map.of("statuses", List.of("OPEN", "CLOSED")));

        CompiledCommand command = new MysqlRelationalCompiler().compile(plan, QUERY_CONTEXT);
        String sql = (String) command.commandBody();

        assertTrue(sql.contains("IN (?, ?)"));
        assertEquals(List.of("OPEN", "CLOSED"), command.parameters().stream()
                .map(binding -> binding.value()).toList());
    }

    /**
     * 验证策略返回的非法物理标识符不会被直接拼接到 SQL。
     */
    @Test
    void rejectsUnsafePhysicalIdentifier() {
        RelationalSchemaPolicy unsafePolicy = (logicalName, context) -> Optional.of(
                new RelationalTableDefinition(
                        "fact", "fact`drop", Map.of(
                                "id", new RelationalColumnDefinition("id", "id", "long"))));
        RelationalQueryPlan plan = new RelationalPlanBuilder(unsafePolicy).build(
                new DslRequest("relational-query", 1, Map.of(
                        "from", Map.of("table", "fact", "alias", "f"),
                        "select", List.of(Map.of("field", "f.id")),
                        "limit", 10), Map.of()), DSL_CONTEXT);

        BackendCompilationException exception = assertThrows(
                BackendCompilationException.class,
                () -> new MysqlRelationalCompiler().compile(plan, QUERY_CONTEXT));

        assertEquals("INVALID_IDENTIFIER", exception.code());
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
                            "id", new RelationalColumnDefinition("id", "device_id", "long"))));
            default -> Optional.empty();
        };
        return new RelationalPlanBuilder(policy).build(
                new DslRequest("relational-query", 1, payload, parameters), DSL_CONTEXT);
    }
}
