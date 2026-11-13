package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证通用 DSL 执行边界、超时、并发和安全审计。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class QueryExecutionEngineTest {

    private static final DslContext DSL_CONTEXT = new DslContext("trace-1", Map.of());
    private static final QueryContext QUERY_CONTEXT = new QueryContext("trace-1", "mysql", Map.of());

    /**
     * 验证执行成功后只产生不含敏感参数的审计摘要。
     */
    @Test
    void executesPlanAndRecordsSafeAudit() {
        List<QueryAuditEvent> events = new ArrayList<>();
        QueryExecutionEngine engine = engine(new RecordingAdapter(plan -> emptyResult()),
                RuntimeExecutionLimits.defaults(), events);
        try {
            QueryResult result = engine.execute(request("secret-value"), DSL_CONTEXT, QUERY_CONTEXT);

            assertEquals(0, result.rowCount());
            assertEquals("SUCCESS", events.getFirst().outcome());
            assertEquals("test", events.getFirst().dslType());
            assertEquals(1, events.getFirst().dslVersion());
            assertEquals(64, events.getFirst().planHash().length());
            assertFalse(events.getFirst().toString().contains("secret-value"));
        } finally {
            engine.close();
        }
    }

    /**
     * 验证请求过大时在访问数据适配器前拒绝。
     */
    @Test
    void rejectsOversizedRequestBeforeBackend() {
        RecordingAdapter adapter = new RecordingAdapter(plan -> emptyResult());
        RuntimeExecutionLimits limits = new RuntimeExecutionLimits(8, 1, Duration.ofSeconds(1), 10, 2, 1);
        List<QueryAuditEvent> events = new ArrayList<>();
        QueryExecutionEngine engine = engine(adapter, limits, events);
        try {
            ExecutionLimitException exception = assertThrows(
                    ExecutionLimitException.class,
                    () -> engine.execute(request("large-secret-value"), DSL_CONTEXT, QUERY_CONTEXT));

            assertEquals("REQUEST_TOO_LARGE", exception.code());
            assertEquals(0, adapter.calls());
            assertEquals("REQUEST_TOO_LARGE", events.getFirst().outcome());
        } finally {
            engine.close();
        }
    }

    /**
     * 验证数据库执行超时会取消任务并返回稳定错误码。
     */
    @Test
    void rejectsBackendTimeout() {
        RecordingAdapter adapter = new RecordingAdapter(plan -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return emptyResult();
        });
        List<QueryAuditEvent> events = new ArrayList<>();
        QueryExecutionEngine engine = engine(adapter,
                new RuntimeExecutionLimits(1024, 1, Duration.ofMillis(20), 10, 2, 1), events);
        try {
            ExecutionLimitException exception = assertThrows(
                    ExecutionLimitException.class,
                    () -> engine.execute(request("secret"), DSL_CONTEXT, QUERY_CONTEXT));

            assertEquals("EXECUTION_TIMEOUT", exception.code());
            assertEquals("EXECUTION_TIMEOUT", events.getFirst().outcome());
        } finally {
            engine.close();
        }
    }

    /**
     * 验证通用执行引擎不会接受自定义后端返回的超限结果。
     */
    @Test
    void rejectsResultExceedingRuntimeRowLimit() {
        List<QueryAuditEvent> events = new ArrayList<>();
        RecordingAdapter adapter = new RecordingAdapter(plan -> new QueryResult(
                List.of(), List.of(List.of(), List.of()), 2, false));
        QueryExecutionEngine engine = engine(adapter,
                new RuntimeExecutionLimits(1024, 1, Duration.ofSeconds(1), 10, 2, 1, 1), events);
        try {
            ExecutionLimitException exception = assertThrows(
                    ExecutionLimitException.class,
                    () -> engine.execute(request("safe"), DSL_CONTEXT, QUERY_CONTEXT));

            assertEquals("RESULT_ROW_LIMIT_EXCEEDED", exception.code());
            assertEquals("RESULT_ROW_LIMIT_EXCEEDED", events.getFirst().outcome());
        } finally {
            engine.close();
        }
    }

    /**
     * 验证并发槽位耗尽时不会继续排队获取连接。
     */
    @Test
    void rejectsConcurrencyBeforeSecondBackendCall() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecordingAdapter adapter = new RecordingAdapter(plan -> {
            started.countDown();
            release.await(1, TimeUnit.SECONDS);
            return emptyResult();
        });
        QueryExecutionEngine engine = engine(adapter,
                new RuntimeExecutionLimits(1024, 1, Duration.ofSeconds(1), 10, 2, 1), new ArrayList<>());
        Thread first = new Thread(() -> engine.execute(request("one"), DSL_CONTEXT, QUERY_CONTEXT));
        first.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));

        try {
            ExecutionLimitException exception = assertThrows(
                    ExecutionLimitException.class,
                    () -> engine.execute(request("two"), DSL_CONTEXT, QUERY_CONTEXT));

            assertEquals("CONCURRENCY_LIMIT", exception.code());
            assertEquals(1, adapter.calls());
        } finally {
            release.countDown();
            first.join(1_000);
            engine.close();
        }
    }

    private static QueryExecutionEngine engine(
            RecordingAdapter adapter, RuntimeExecutionLimits limits, List<QueryAuditEvent> events) {
        DslAdapterRegistry dslRegistry = new DslAdapterRegistry(List.of(new DslAdapter() {
            @Override
            public String type() {
                return "test";
            }

            @Override
            public Set<Integer> supportedVersions() {
                return Set.of(1);
            }

            @Override
            public ExecutionPlan parse(DslRequest request, DslContext context) {
                return new GenericExecutionPlan("test", 1, Map.of("nodeCount", 1));
            }
        }));
        return new QueryExecutionEngine(
                dslRegistry,
                new DataAdapterRegistry(List.of(adapter)),
                "mysql",
                (plan, context) -> { },
                events::add,
                NoopQueryTelemetry.INSTANCE,
                limits);
    }

    private static DslRequest request(String secret) {
        return new DslRequest("test", 1, Map.of("shape", "fixed"), Map.of("secret", secret));
    }

    private static QueryResult emptyResult() {
        return new QueryResult(List.of(), List.of(), 0, false);
    }

    private static final class RecordingAdapter implements DataAdapter {

        private final ThrowingFunction<ExecutionPlan, QueryResult> function;
        private int calls;

        private RecordingAdapter(ThrowingFunction<ExecutionPlan, QueryResult> function) {
            this.function = function;
        }

        @Override
        public String type() {
            return "mysql";
        }

        @Override
        public Set<String> supportedPlanTypes() {
            return Set.of("test");
        }

        @Override
        public BackendCapabilities capabilities() {
            return new BackendCapabilities("mysql", Set.of("test"));
        }

        @Override
        public QueryResult execute(ExecutionPlan plan, QueryContext context) {
            calls++;
            try {
                return function.apply(plan);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExecutionLimitException("EXECUTION_INTERRUPTED", "test adapter interrupted");
            }
        }

        private int calls() {
            return calls;
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T value) throws InterruptedException;
    }
}
