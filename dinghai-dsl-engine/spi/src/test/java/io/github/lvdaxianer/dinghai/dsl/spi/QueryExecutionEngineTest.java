package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.PlanExtensionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * 验证扩展在后端执行前生效，并且最终计划再次经过权限策略校验。
     */
    @Test
    void appliesExtensionBeforeBackendAndRevalidatesFinalPlan() {
        List<QueryAuditEvent> events = new ArrayList<>();
        AtomicInteger policyCalls = new AtomicInteger();
        List<ExecutionPlan> receivedPlans = new ArrayList<>();
        RecordingAdapter adapter = new RecordingAdapter(plan -> {
            receivedPlans.add(plan);
            return emptyResult();
        });
        PlanExtension extension = new PlanExtension() {
            @Override
            public String id() {
                return "test-scope";
            }

            @Override
            public ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
                return new GenericExecutionPlan("test", 1, Map.of("nodeCount", 1, "scope", "trusted"));
            }
        };
        QueryExecutionEngine engine = engine(
                adapter,
                new RuntimeExecutionLimits(1024, 1, Duration.ofSeconds(1), 10, 2, 1),
                events,
                new PlanExtensionRegistry(List.of(extension)),
                (plan, context) -> policyCalls.incrementAndGet());
        try {
            engine.execute(request("secret"), DSL_CONTEXT, QUERY_CONTEXT);

            assertEquals(2, policyCalls.get());
            assertEquals("trusted", receivedPlans.getFirst().metadata().get("scope"));
        } finally {
            engine.close();
        }
    }

    /**
     * 验证扩展失败时不会调用数据适配器。
     */
    @Test
    void rejectsExtensionFailureBeforeBackend() {
        RecordingAdapter adapter = new RecordingAdapter(plan -> emptyResult());
        PlanExtension extension = new PlanExtension() {
            @Override
            public String id() {
                return "broken-extension";
            }

            @Override
            public ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
                throw new IllegalStateException("database password");
            }
        };
        QueryExecutionEngine engine = engine(
                adapter,
                RuntimeExecutionLimits.defaults(),
                new ArrayList<>(),
                new PlanExtensionRegistry(List.of(extension)),
                (plan, context) -> { });
        try {
            PlanExtensionException exception = assertThrows(
                    PlanExtensionException.class,
                    () -> engine.execute(request("secret"), DSL_CONTEXT, QUERY_CONTEXT));

            assertEquals("EXTENSION_FAILED", exception.code());
            assertEquals(0, adapter.calls());
        } finally {
            engine.close();
        }
    }

    /**
     * 验证安全编译入口只返回最终计划，不触发数据适配器。
     */
    @Test
    void compilesFinalPlanWithoutBackendExecution() {
        RecordingAdapter adapter = new RecordingAdapter(plan -> emptyResult());
        PlanExtension extension = new PlanExtension() {
            @Override
            public String id() {
                return "compile-only";
            }

            @Override
            public ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
                return new GenericExecutionPlan("test", 1, Map.of("nodeCount", 1, "compiled", true));
            }
        };
        QueryExecutionEngine engine = engine(
                adapter,
                RuntimeExecutionLimits.defaults(),
                new ArrayList<>(),
                new PlanExtensionRegistry(List.of(extension)),
                (plan, context) -> { });
        try {
            ExecutionPlan plan = engine.compile(request("secret"), DSL_CONTEXT, QUERY_CONTEXT);

            assertEquals(true, plan.metadata().get("compiled"));
            assertEquals(0, adapter.calls());
        } finally {
            engine.close();
        }
    }

    /**
     * 验证后端任务获得服务端上下文，并在工作线程完成后清理线程状态。
     */
    @Test
    void decoratesBackendTaskAndCleansWorkerContext() {
        ThreadLocal<String> traceContext = new ThreadLocal<>();
        AtomicReference<String> observed = new AtomicReference<>();
        RecordingAdapter adapter = new RecordingAdapter(plan -> {
            observed.set(traceContext.get());
            return emptyResult();
        });
        QueryTaskDecorator decorator = (task, context) -> () -> {
            traceContext.set(context.traceId());
            try {
                return task.call();
            } finally {
                traceContext.remove();
            }
        };
        QueryExecutionEngine engine = engine(
                adapter,
                RuntimeExecutionLimits.defaults(),
                new ArrayList<>(),
                new PlanExtensionRegistry(List.of()),
                (plan, context) -> { },
                decorator);
        try {
            engine.execute(request("secret"), DSL_CONTEXT, QUERY_CONTEXT);

            assertEquals("trace-1", observed.get());
            assertNull(traceContext.get());
        } finally {
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
                new QueryExecutionDependencies(
                        (plan, context) -> { },
                        new PlanExtensionRegistry(List.of()),
                        events::add,
                        NoopQueryTelemetry.INSTANCE,
                        limits));
    }

    private static QueryExecutionEngine engine(
            RecordingAdapter adapter,
            RuntimeExecutionLimits limits,
            List<QueryAuditEvent> events,
            PlanExtensionRegistry extensions,
            PlanPolicy policy) {
        return engine(adapter, limits, events, extensions, policy, QueryTaskDecorator.identity());
    }

    private static QueryExecutionEngine engine(
            RecordingAdapter adapter,
            RuntimeExecutionLimits limits,
            List<QueryAuditEvent> events,
            PlanExtensionRegistry extensions,
            PlanPolicy policy,
            QueryTaskDecorator taskDecorator) {
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
                new QueryExecutionDependencies(
                        policy,
                        extensions,
                        events::add,
                        NoopQueryTelemetry.INSTANCE,
                        limits,
                        taskDecorator));
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
