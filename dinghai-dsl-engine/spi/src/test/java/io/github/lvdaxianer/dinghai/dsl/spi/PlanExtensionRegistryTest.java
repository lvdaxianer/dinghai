package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.PlanExtensionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证计划扩展注册表的冻结、排序和失败关闭边界。
 */
class PlanExtensionRegistryTest {

    private static final QueryContext CONTEXT = new QueryContext("trace-1", "mysql", Map.of());
    private static final PlanExtensionContext EXTENSION_CONTEXT =
            new PlanExtensionContext("test", 1, CONTEXT);
    private static final GenericExecutionPlan PLAN =
            new GenericExecutionPlan("test", 1, Map.of("nodeCount", 1));

    @Test
    void appliesSupportedExtensionsInStableOrder() {
        PlanExtension first = extension("z-last", 20, true, plan ->
                new GenericExecutionPlan("test", 1, Map.of("stage", "last")));
        PlanExtension second = extension("a-first", 10, true, plan ->
                new GenericExecutionPlan("test", 1, Map.of("stage", "first")));
        PlanExtension skipped = extension("skipped", 1, false, plan -> {
            throw new AssertionError("unsupported extension must not run");
        });

        PlanExtensionRegistry registry = new PlanExtensionRegistry(List.of(first, skipped, second));

        ExecutionPlan result = registry.apply(PLAN, EXTENSION_CONTEXT);

        assertEquals("last", result.metadata().get("stage"));
        assertEquals(List.of("skipped", "a-first", "z-last"), registry.registeredIds());
    }

    @Test
    void rejectsDuplicateExtensionIdentifiers() {
        PlanExtension duplicateA = extension("same", 1, true, plan -> plan);
        PlanExtension duplicateB = extension("same", 2, true, plan -> plan);

        PlanExtensionException exception = assertThrows(
                PlanExtensionException.class,
                () -> new PlanExtensionRegistry(List.of(duplicateA, duplicateB)));

        assertEquals("DUPLICATE_EXTENSION", exception.code());
    }

    @Test
    void returnsSamePlanWhenNoExtensionApplies() {
        PlanExtensionRegistry registry = new PlanExtensionRegistry(List.of(
                extension("not-applicable", 1, false, plan -> plan)));

        assertSame(PLAN, registry.apply(PLAN, EXTENSION_CONTEXT));
    }

    @Test
    void wrapsUnexpectedExtensionFailureAsStableError() {
        PlanExtensionRegistry registry = new PlanExtensionRegistry(List.of(
                extension("broken", 1, true, plan -> {
                    throw new IllegalStateException("secret backend detail");
                })));

        PlanExtensionException exception = assertThrows(
                PlanExtensionException.class,
                () -> registry.apply(PLAN, EXTENSION_CONTEXT));

        assertEquals("EXTENSION_FAILED", exception.code());
        assertEquals("plan extension failed", exception.getMessage());
    }

    private static PlanExtension extension(
            String id,
            int order,
            boolean supported,
            java.util.function.Function<ExecutionPlan, ExecutionPlan> function) {
        return new PlanExtension() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public boolean supports(ExecutionPlan plan, PlanExtensionContext context) {
                return supported;
            }

            @Override
            public ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
                return function.apply(plan);
            }
        };
    }
}
