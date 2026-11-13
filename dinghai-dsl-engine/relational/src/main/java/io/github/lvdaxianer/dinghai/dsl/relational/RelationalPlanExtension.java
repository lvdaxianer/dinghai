package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtension;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionContext;

import java.util.List;

/**
 * 关系查询计划扩展契约。
 */
public interface RelationalPlanExtension extends PlanExtension {

    /**
     * 返回当前关系计划需要增加的类型化约束。
     *
     * @param plan 当前关系计划
     * @param context 可信扩展上下文
     * @return 类型化约束列表
     */
    List<RelationalConstraint> constraints(RelationalQueryPlan plan, PlanExtensionContext context);

    /**
     * 关系扩展只适用于关系查询计划。
     *
     * @param plan 当前计划
     * @param context 可信扩展上下文
     * @return 是否为关系查询计划
     */
    @Override
    default boolean supports(ExecutionPlan plan, PlanExtensionContext context) {
        return plan instanceof RelationalQueryPlan;
    }

    /**
     * 将约束通过安全重写器应用到关系计划。
     *
     * @param plan 当前计划
     * @param context 可信扩展上下文
     * @return 重写后的关系计划
     */
    @Override
    default ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
        if (!(plan instanceof RelationalQueryPlan relational)) {
            return plan;
        }
        return RelationalPlanRewriter.rewrite(relational, id(), constraints(relational, context));
    }
}
