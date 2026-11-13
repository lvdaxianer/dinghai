package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.util.Map;

/**
 * 不包含 SQL 或参数值的安全计划摘要。
 *
 * @param planType 计划类型
 * @param planVersion 计划版本
 * @param metadata 不含参数值的计划元数据
 */
public record PlanSummary(String planType, int planVersion, Map<String, Object> metadata) {

    /**
     * 从执行计划构建摘要。
     *
     * @param plan 执行计划
     * @return 安全摘要
     */
    public static PlanSummary from(ExecutionPlan plan) {
        return new PlanSummary(plan.planType(), plan.planVersion(), Map.copyOf(plan.metadata()));
    }
}
