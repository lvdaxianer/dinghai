package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DslAdapterRegistry;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.AdapterRegistryException;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.PlanExtensionRegistry;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通用 DSL 解析、校验、计划和后端执行编排器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class QueryExecutionEngine implements AutoCloseable {

    private static final System.Logger LOGGER =
            System.getLogger("io.github.lvdaxianer.dinghai.dsl.execution");

    private final DslAdapterRegistry dslAdapters;
    private final DataAdapterRegistry dataAdapters;
    private final String defaultDataAdapter;
    private final PlanPolicy planPolicy;
    private final PlanExtensionRegistry planExtensions;
    private final QueryAuditSink auditSink;
    private final QueryTelemetry telemetry;
    private final RuntimeExecutionLimits limits;
    private final QueryTaskDecorator taskDecorator;
    private final Semaphore concurrency;
    private final ExecutorService executor;

    /**
     * 创建通用执行编排器。
     *
     * @param dslAdapters DSL 注册表
     * @param dataAdapters 数据适配器注册表
     * @param defaultDataAdapter 启动配置选择的默认后端
     * @param planPolicy 宿主计划权限策略
     * @param auditSink 安全审计接收器
     * @param telemetry 指标接收器
     * @param limits 运行时边界
     */
    public QueryExecutionEngine(
            DslAdapterRegistry dslAdapters,
            DataAdapterRegistry dataAdapters,
            String defaultDataAdapter,
            PlanPolicy planPolicy,
            QueryAuditSink auditSink,
            QueryTelemetry telemetry,
            RuntimeExecutionLimits limits) {
        this(
                dslAdapters,
                dataAdapters,
                defaultDataAdapter,
                new QueryExecutionDependencies(
                        planPolicy,
                        new PlanExtensionRegistry(java.util.List.of()),
                        auditSink,
                        telemetry,
                        limits));
    }

    /**
     * 创建包含计划扩展的通用执行编排器。
     *
     * @param dslAdapters DSL 注册表
     * @param dataAdapters 数据适配器注册表
     * @param defaultDataAdapter 启动配置选择的默认后端
     * @param dependencies 服务端执行依赖
     */
    public QueryExecutionEngine(
            DslAdapterRegistry dslAdapters,
            DataAdapterRegistry dataAdapters,
            String defaultDataAdapter,
            QueryExecutionDependencies dependencies) {
        this.dslAdapters = Objects.requireNonNull(dslAdapters, "dslAdapters");
        this.dataAdapters = Objects.requireNonNull(dataAdapters, "dataAdapters");
        this.defaultDataAdapter = requireText(defaultDataAdapter, "defaultDataAdapter");
        QueryExecutionDependencies value = Objects.requireNonNull(dependencies, "dependencies");
        this.planPolicy = value.planPolicy();
        this.planExtensions = value.planExtensions();
        this.auditSink = value.auditSink();
        this.telemetry = value.telemetry();
        this.limits = value.limits();
        this.taskDecorator = value.taskDecorator();
        this.concurrency = new Semaphore(limits.maxConcurrency());
        this.executor = Executors.newFixedThreadPool(limits.maxConcurrency(), runnable -> {
            Thread thread = new Thread(runnable, "dinghai-dsl-query");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 执行统一 DSL 请求。
     *
     * @param request DSL 请求
     * @param dslContext DSL 编译上下文
     * @param queryContext 后端查询上下文
     * @return 统一结果
     */
    public QueryResult execute(DslRequest request, DslContext dslContext, QueryContext queryContext) {
        DslRequest value = Objects.requireNonNull(request, "request");
        DslContext compileContext = Objects.requireNonNull(dslContext, "dslContext");
        QueryContext executionContext = Objects.requireNonNull(queryContext, "queryContext");
        long started = System.nanoTime();
        String planHash = PlanFingerprint.request(value);
        String adapterType = defaultDataAdapter;
        String outcome = "EXECUTION_FAILED";
        long rowCount = 0;
        boolean acquired = false;
        try {
            validateRequestSize(value);
            acquireConcurrency();
            acquired = true;
            ExecutionPlan plan = parseAndValidate(value, compileContext, executionContext);
            planHash = PlanFingerprint.plan(plan);
            DataAdapter adapter = dataAdapters.resolve(defaultDataAdapter, plan);
            adapterType = adapter.type();
            QueryResult result = executeWithTimeout(adapter, plan, executionContext);
            rowCount = result.rowCount();
            if (rowCount > limits.maxRows()) {
                throw new ExecutionLimitException(
                        "RESULT_ROW_LIMIT_EXCEEDED", "DSL result exceeds configured row limit");
            }
            outcome = "SUCCESS";
            return result;
        } catch (RuntimeException exception) {
            outcome = errorCode(exception);
            throw exception;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            recordAudit(executionContext, value, planHash, adapterType, outcome, durationMs, rowCount);
            if (acquired) {
                concurrency.release();
            }
        }
    }

    /**
     * 解析、校验并应用扩展，但不解析数据适配器或获取数据库连接。
     *
     * @param request DSL 请求
     * @param dslContext DSL 编译上下文
     * @param queryContext 后端查询上下文
     * @return 最终不可变执行计划
     */
    public ExecutionPlan compile(DslRequest request, DslContext dslContext, QueryContext queryContext) {
        DslRequest value = Objects.requireNonNull(request, "request");
        DslContext compileContext = Objects.requireNonNull(dslContext, "dslContext");
        QueryContext executionContext = Objects.requireNonNull(queryContext, "queryContext");
        validateRequestSize(value);
        acquireConcurrency();
        try {
            return parseAndValidate(value, compileContext, executionContext);
        } finally {
            concurrency.release();
        }
    }

    private ExecutionPlan parseAndValidate(
            DslRequest request, DslContext dslContext, QueryContext queryContext) {
        DslAdapter adapter = dslAdapters.resolve(request);
        ExecutionPlan plan = adapter.parse(request, dslContext);
        planPolicy.validate(plan, queryContext);
        validatePlanBounds(plan);
        ExecutionPlan extended = planExtensions.apply(
                plan,
                new PlanExtensionContext(request.dslType(), request.version(), queryContext));
        planPolicy.validate(extended, queryContext);
        validatePlanBounds(extended);
        return extended;
    }

    private QueryResult executeWithTimeout(DataAdapter adapter, ExecutionPlan plan, QueryContext context) {
        Future<QueryResult> future = executor.submit(taskDecorator.decorate(
                () -> adapter.execute(plan, context), context));
        try {
            return future.get(limits.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ExecutionLimitException("EXECUTION_TIMEOUT", "DSL execution exceeded configured timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ExecutionLimitException("EXECUTION_INTERRUPTED", "DSL execution was interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new BackendExecutionException("BACKEND_EXECUTION_FAILED", "DSL backend execution failed", cause);
        }
    }

    private void validateRequestSize(DslRequest request) {
        long estimatedBytes = estimateSize(request.payload()) + estimateSize(request.parameters());
        if (estimatedBytes > limits.maxRequestBytes()) {
            throw new ExecutionLimitException("REQUEST_TOO_LARGE", "DSL request exceeds configured size limit");
        }
    }

    private void validatePlanBounds(ExecutionPlan plan) {
        int nodes = number(plan.metadata().get("nodeCount"),
                number(plan.metadata().get("selectCount"), 0)
                        + number(plan.metadata().get("conditionCount"), 0)
                        + number(plan.metadata().get("joinCount"), 0));
        int depth = number(plan.metadata().get("predicateDepth"), 0);
        int joins = number(plan.metadata().get("joinCount"), 0);
        if (nodes > limits.maxPlanNodes() || depth > limits.maxPlanDepth() || joins > limits.maxJoins()) {
            throw new ExecutionLimitException("PLAN_COMPLEXITY_EXCEEDED", "DSL plan exceeds runtime limits");
        }
    }

    private void acquireConcurrency() {
        if (!concurrency.tryAcquire()) {
            throw new ExecutionLimitException("CONCURRENCY_LIMIT", "DSL execution concurrency limit reached");
        }
    }

    private void recordAudit(
            QueryContext context,
            DslRequest request,
            String planHash,
            String adapterType,
            String outcome,
            long durationMs,
            long rowCount) {
        try {
            auditSink.record(new QueryAuditEvent(
                    context.traceId(), request.dslType(), request.version(), planHash, adapterType,
                    outcome, durationMs, rowCount));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "query audit sink failed traceId={0} adapter={1} exceptionType={2}",
                    context.traceId(), adapterType, exception.getClass().getName());
        }
        try {
            telemetry.record("execute", adapterType, durationMs, outcome, rowCount);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "query telemetry sink failed traceId={0} adapter={1} exceptionType={2}",
                    context.traceId(), adapterType, exception.getClass().getName());
        }
    }

    private static long estimateSize(Object value) {
        if (value == null) {
            return 4;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return map.entrySet().stream()
                    .mapToLong(entry -> estimateSize(entry.getKey()) + estimateSize(entry.getValue()))
                    .sum() + 2;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().mapToLong(QueryExecutionEngine::estimateSize).sum() + 2;
        }
        return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof BackendExecutionException backend) {
            return backend.code();
        }
        if (exception instanceof ExecutionLimitException limit) {
            return limit.code();
        }
        if (exception instanceof AdapterRegistryException registry) {
            return registry.code();
        }
        if (exception instanceof PlanExtensionException extension) {
            return extension.code();
        }
        if (exception instanceof DslCompilationException compilation
                && !compilation.diagnostics().isEmpty()) {
            return compilation.diagnostics().getFirst().code();
        }
        return "EXECUTION_FAILED";
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    /**
     * 关闭执行线程池。
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
