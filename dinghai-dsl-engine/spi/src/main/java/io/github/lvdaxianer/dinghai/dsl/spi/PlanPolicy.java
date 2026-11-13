package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;

/**
 * 宿主应用提供的计划策略校验扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface PlanPolicy {

    /**
     * 校验计划是否可以在当前上下文执行。
     *
     * @param plan 执行计划
     * @param context 查询上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    void validate(ExecutionPlan plan, QueryContext context);
}
