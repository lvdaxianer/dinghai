package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.AdapterRegistryException;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 DSL 和数据适配器注册表的精确选择行为。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class AdapterRegistryTest {

    /**
     * 验证 DSL 注册表按类型和版本选择唯一适配器。
     */
    @Test
    void dslRegistryResolvesExactTypeAndVersion() {
        DslAdapter adapter = new StubDslAdapter("relational-query", Set.of(1));
        DslAdapterRegistry registry = new DslAdapterRegistry(List.of(adapter));

        assertEquals(adapter, registry.resolve(new DslRequest(
                "relational-query", 1, Map.of(), Map.of())));
    }

    /**
     * 验证 DSL 未知类型和不支持版本不会回退到其他适配器。
     */
    @Test
    void dslRegistryRejectsUnknownTypeAndUnsupportedVersion() {
        DslAdapterRegistry registry = new DslAdapterRegistry(List.of(
                new StubDslAdapter("relational-query", Set.of(1))));

        AdapterRegistryException unknownType = assertThrows(
                AdapterRegistryException.class,
                () -> registry.resolve(new DslRequest("unknown", 1, Map.of(), Map.of())));
        AdapterRegistryException unknownVersion = assertThrows(
                AdapterRegistryException.class,
                () -> registry.resolve(new DslRequest("relational-query", 2, Map.of(), Map.of())));

        assertEquals(AdapterRegistryException.DSL_TYPE_NOT_FOUND, unknownType.code());
        assertEquals(AdapterRegistryException.DSL_VERSION_NOT_SUPPORTED, unknownVersion.code());
    }

    /**
     * 验证 DSL 注册表拒绝同一类型和版本的重复声明。
     */
    @Test
    void dslRegistryRejectsDuplicateRegistration() {
        assertThrows(AdapterRegistryException.class, () -> new DslAdapterRegistry(List.of(
                new StubDslAdapter("relational-query", Set.of(1)),
                new StubDslAdapter("relational-query", Set.of(1)))));
    }

    /**
     * 验证数据注册表按配置类型选择后端，不自动切换其他后端。
     */
    @Test
    void dataRegistryResolvesExactTypeWithoutFallback() {
        DataAdapter mysql = new StubDataAdapter("mysql", Set.of("relational-query"));
        DataAdapterRegistry registry = new DataAdapterRegistry(List.of(mysql));

        assertEquals(mysql, registry.resolve("mysql"));
        AdapterRegistryException missing = assertThrows(
                AdapterRegistryException.class,
                () -> registry.resolve("postgresql"));

        assertEquals(AdapterRegistryException.DATA_ADAPTER_TYPE_NOT_FOUND, missing.code());
    }

    /**
     * 验证数据注册表拒绝重复后端类型和不支持的计划类型。
     */
    @Test
    void dataRegistryRejectsDuplicateAndUnsupportedPlan() {
        assertThrows(AdapterRegistryException.class, () -> new DataAdapterRegistry(List.of(
                new StubDataAdapter("mysql", Set.of("relational-query")),
                new StubDataAdapter("mysql", Set.of("other-plan")))));
        DataAdapterRegistry registry = new DataAdapterRegistry(List.of(
                new StubDataAdapter("mysql", Set.of("relational-query"))));

        AdapterRegistryException unsupported = assertThrows(
                AdapterRegistryException.class,
                () -> registry.resolve("mysql", new GenericExecutionPlan("other-plan", 1, Map.of())));

        assertEquals(AdapterRegistryException.DATA_ADAPTER_PLAN_NOT_SUPPORTED, unsupported.code());
    }

    /**
     * 验证注册表对外暴露的类型集合不可变。
     */
    @Test
    void registriesExposeImmutableSnapshots() {
        DslAdapterRegistry dslRegistry = new DslAdapterRegistry(List.of(
                new StubDslAdapter("relational-query", Set.of(1))));
        DataAdapterRegistry dataRegistry = new DataAdapterRegistry(List.of(
                new StubDataAdapter("mysql", Set.of("relational-query"))));

        assertThrows(UnsupportedOperationException.class,
                () -> dslRegistry.registeredTypes().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> dataRegistry.registeredTypes().clear());
    }

    /**
     * 简化的 DSL 适配器。
     */
    private static final class StubDslAdapter implements DslAdapter {

        private final String type;
        private final Set<Integer> versions;

        private StubDslAdapter(String type, Set<Integer> versions) {
            this.type = type;
            this.versions = versions;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Set<Integer> supportedVersions() {
            return versions;
        }

        @Override
        public ExecutionPlan parse(DslRequest request, DslContext context) {
            return new GenericExecutionPlan("stub-plan", 1, Map.of());
        }
    }

    /**
     * 简化的数据适配器。
     */
    private static final class StubDataAdapter implements DataAdapter {

        private final String type;
        private final Set<String> planTypes;

        private StubDataAdapter(String type, Set<String> planTypes) {
            this.type = type;
            this.planTypes = planTypes;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Set<String> supportedPlanTypes() {
            return planTypes;
        }

        @Override
        public BackendCapabilities capabilities() {
            return new BackendCapabilities(type, Set.of("read"));
        }

        @Override
        public QueryResult execute(ExecutionPlan plan, QueryContext context) {
            return new QueryResult(List.of(), List.of(), 0, false);
        }
    }
}
