package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.util.Objects;

/**
 * 服务端计划扩展契约。
 *
 * <p>扩展只能处理已经解析的类型化计划和服务端可信上下文，不接触原始 DSL、SQL 或数据库连接。</p>
 */
public interface PlanExtension {

    /**
     * 返回扩展唯一标识。
     *
     * @return 扩展标识
     */
    String id();

    /**
     * 返回扩展执行顺序。
     *
     * @return 越小越先执行
     */
    default int order() {
        return 0;
    }

    /**
     * 判断扩展是否适用于当前计划。
     *
     * @param plan 当前计划
     * @param context 可信扩展上下文
     * @return 是否适用
     */
    default boolean supports(ExecutionPlan plan, PlanExtensionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        return true;
    }

    /**
     * 应用扩展并返回新的不可变计划。
     *
     * @param plan 当前计划
     * @param context 可信扩展上下文
     * @return 扩展后的计划
     */
    ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context);
}
