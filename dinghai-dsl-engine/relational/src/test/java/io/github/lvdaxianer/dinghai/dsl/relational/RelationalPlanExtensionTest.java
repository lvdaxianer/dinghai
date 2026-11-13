package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionContext;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证关系计划扩展只能通过类型化约束重写计划。
 */
class RelationalPlanExtensionTest {

    private static final DslContext DSL_CONTEXT = new DslContext("trace-1", Map.of());
    private static final PlanExtensionContext EXTENSION_CONTEXT = new PlanExtensionContext(
            "relational-query", 1, new QueryContext("trace-1", "mysql", Map.of("tenantId", "tenant-a")));

    @Test
    void injectsWhereAndLeftJoinOnConstraintsAsBindings() {
        RelationalQueryPlan base = plan(Map.of(), Map.of());
        RelationalPlanExtension extension = new RelationalPlanExtension() {
            @Override
            public String id() {
                return "scope";
            }

            @Override
            public List<RelationalConstraint> constraints(
                    RelationalQueryPlan current, PlanExtensionContext context) {
                return List.of(
                        RelationalConstraint.of(
                                new RelationalQueryAst.RelationalField("f", "deleted"),
                                ComparisonOperator.EQ,
                                "integer",
                                0,
                                ConstraintPlacement.WHERE),
                        RelationalConstraint.of(
                                new RelationalQueryAst.RelationalField("d", "deleted"),
                                ComparisonOperator.EQ,
                                "integer",
                                0,
                                ConstraintPlacement.JOIN_ON));
            }
        };

        RelationalQueryPlan extended = (RelationalQueryPlan) extension.apply(base, EXTENSION_CONTEXT);

        assertEquals(1, extended.query().where().size());
        assertEquals(1, extended.query().joins().getFirst().additionalOn().size());
        assertEquals(2, extended.parameters().size());
        assertTrue(extended.parameters().stream().allMatch(binding -> binding.name().startsWith("__dinghai_ext_")));
        assertEquals(2, extended.metadata().get("conditionCount"));
    }

    @Test
    void rejectsConstraintForUnknownField() {
        RelationalQueryPlan base = plan(Map.of(), Map.of());
        RelationalPlanExtension extension = extensionWith(RelationalConstraint.of(
                new RelationalQueryAst.RelationalField("f", "not_allowed"),
                ComparisonOperator.EQ,
                "string",
                "value",
                ConstraintPlacement.WHERE));

        PlanExtensionException exception = assertThrows(
                PlanExtensionException.class,
                () -> extension.apply(base, EXTENSION_CONTEXT));

        assertEquals("EXTENSION_FIELD_NOT_ALLOWED", exception.code());
    }

    @Test
    void avoidsUserParameterNameCollision() {
        RelationalQueryPlan base = plan(
                Map.of(
                        "where", List.of(Map.of(
                                "field", "f.status",
                                "operator", "eq",
                                "parameter", "__dinghai_ext_scope_0",
                                "logicalType", "string"))),
                Map.of("__dinghai_ext_scope_0", "OPEN"));
        RelationalPlanExtension extension = extensionWith(RelationalConstraint.of(
                new RelationalQueryAst.RelationalField("f", "deleted"),
                ComparisonOperator.EQ,
                "integer",
                0,
                ConstraintPlacement.WHERE));

        RelationalQueryPlan extended = (RelationalQueryPlan) extension.apply(base, EXTENSION_CONTEXT);

        assertEquals(2, extended.parameters().size());
        assertNotEquals(
                extended.parameters().getFirst().name(),
                extended.parameters().getLast().name());
    }

    @Test
    void rejectsJoinOnConstraintForNonJoinedAlias() {
        RelationalQueryPlan base = plan(Map.of(), Map.of());
        RelationalPlanExtension extension = extensionWith(RelationalConstraint.of(
                new RelationalQueryAst.RelationalField("f", "deleted"),
                ComparisonOperator.EQ,
                "integer",
                0,
                ConstraintPlacement.JOIN_ON));

        PlanExtensionException exception = assertThrows(
                PlanExtensionException.class,
                () -> extension.apply(base, EXTENSION_CONTEXT));

        assertEquals("EXTENSION_JOIN_TARGET_NOT_FOUND", exception.code());
    }

    private static RelationalPlanExtension extensionWith(RelationalConstraint constraint) {
        return new RelationalPlanExtension() {
            @Override
            public String id() {
                return "scope";
            }

            @Override
            public List<RelationalConstraint> constraints(
                    RelationalQueryPlan plan, PlanExtensionContext context) {
                return List.of(constraint);
            }
        };
    }

    private static RelationalQueryPlan plan(Map<String, Object> extraPayload, Map<String, Object> parameters) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("from", Map.of("table", "fact", "alias", "f"));
        payload.put("select", List.of(Map.of("field", "f.status")));
        payload.put("joins", List.of(Map.of(
                "type", "left",
                "table", Map.of("table", "device", "alias", "d"),
                "on", List.of(Map.of("left", "f.device_id", "right", "d.id")))));
        payload.put("limit", 10);
        payload.putAll(extraPayload);
        RelationalSchemaPolicy policy = (logicalName, context) -> switch (logicalName) {
            case "fact" -> Optional.of(new RelationalTableDefinition(
                    "fact", "fact_table", Map.of(
                            "status", new RelationalColumnDefinition("status", "status", "string"),
                            "device_id", new RelationalColumnDefinition("device_id", "device_id", "long"),
                            "deleted", new RelationalColumnDefinition("deleted", "deleted", "integer"))));
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
