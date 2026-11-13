package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 DSL 和后端适配器的最小扩展契约。
 *
 * @author lvdaxianer@yeah.net
 */
class AdapterContractTest {

    /**
     * 验证 DSL Adapter 可以基于类型和版本报告支持关系。
     */
    @Test
    void dslAdapterReportsSupportedRequest() {
        DslAdapter adapter = new StubDslAdapter();
        DslRequest request = new DslRequest("stub", 1, Map.of(), Map.of());

        assertTrue(adapter.supports(request));
        assertEquals("stub", adapter.type());
    }

    /**
     * 验证 Data Adapter 可以接收统一执行计划并返回统一结果。
     */
    @Test
    void dataAdapterExecutesPlanContract() {
        DataAdapter adapter = new StubDataAdapter();
        QueryResult result = adapter.execute(
                new GenericExecutionPlan("stub-plan", 1, Map.of()),
                new QueryContext("trace-1", "default", Map.of()));

        assertEquals(0, result.rowCount());
        assertTrue(adapter.supportedPlanTypes().contains("stub-plan"));
    }

    /**
     * 简化的 DSL Adapter 测试实现。
     */
    private static final class StubDslAdapter implements DslAdapter {

        @Override
        public String type() {
            return "stub";
        }

        @Override
        public Set<Integer> supportedVersions() {
            return Set.of(1);
        }

        @Override
        public GenericExecutionPlan parse(DslRequest request, DslContext context) {
            return new GenericExecutionPlan("stub-plan", 1, Map.of());
        }
    }

    /**
     * 简化的 Data Adapter 测试实现。
     */
    private static final class StubDataAdapter implements DataAdapter {

        @Override
        public String type() {
            return "stub";
        }

        @Override
        public Set<String> supportedPlanTypes() {
            return Set.of("stub-plan");
        }

        @Override
        public BackendCapabilities capabilities() {
            return new BackendCapabilities("stub", Set.of("read"));
        }

        @Override
        public QueryResult execute(io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan plan,
                                   QueryContext context) {
            return new QueryResult(java.util.List.of(), java.util.List.of(), 0, false);
        }
    }
}
