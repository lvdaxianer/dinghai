package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;

import java.util.Set;

/**
 * 执行统一计划的后端适配器契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface DataAdapter {

    /**
     * 返回后端适配器类型。
     *
     * @return 适配器类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    String type();

    /**
     * 返回支持的计划类型。
     *
     * @return 计划类型集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Set<String> supportedPlanTypes();

    /**
     * 返回后端能力快照。
     *
     * @return 后端能力
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    BackendCapabilities capabilities();

    /**
     * 执行统一计划。
     *
     * @param plan 已校验执行计划
     * @param context 查询上下文
     * @return 统一查询结果
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    QueryResult execute(ExecutionPlan plan, QueryContext context);
}
