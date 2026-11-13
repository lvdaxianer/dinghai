package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.spi.registry.PlanExtensionRegistry;

import java.util.Objects;

/**
 * 查询执行引擎的服务端依赖集合，避免构造函数参数顺序混淆。
 *
 * @param planPolicy 计划权限策略
 * @param planExtensions 计划扩展注册表
 * @param auditSink 审计接收器
 * @param telemetry 指标接收器
 * @param limits 运行时边界
 * @param taskDecorator 后端任务上下文装饰器
 */
public record QueryExecutionDependencies(
        PlanPolicy planPolicy,
        PlanExtensionRegistry planExtensions,
        QueryAuditSink auditSink,
        QueryTelemetry telemetry,
        RuntimeExecutionLimits limits,
        QueryTaskDecorator taskDecorator) {

    /**
     * 创建不传播额外上下文的执行依赖。
     *
     * @param planPolicy 计划权限策略
     * @param planExtensions 计划扩展注册表
     * @param auditSink 审计接收器
     * @param telemetry 指标接收器
     * @param limits 运行时边界
     */
    public QueryExecutionDependencies(
            PlanPolicy planPolicy,
            PlanExtensionRegistry planExtensions,
            QueryAuditSink auditSink,
            QueryTelemetry telemetry,
            RuntimeExecutionLimits limits) {
        this(planPolicy, planExtensions, auditSink, telemetry, limits, QueryTaskDecorator.identity());
    }

    /**
     * 校验引擎依赖。
     */
    public QueryExecutionDependencies {
        planPolicy = Objects.requireNonNull(planPolicy, "planPolicy");
        planExtensions = Objects.requireNonNull(planExtensions, "planExtensions");
        auditSink = Objects.requireNonNull(auditSink, "auditSink");
        telemetry = Objects.requireNonNull(telemetry, "telemetry");
        limits = Objects.requireNonNull(limits, "limits");
        taskDecorator = Objects.requireNonNull(taskDecorator, "taskDecorator");
    }
}
