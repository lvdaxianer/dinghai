package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;

import java.util.Set;

/**
 * 将后端无关计划转换为目标系统命令的编译器契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface PlanCompiler {

    /**
     * 返回目标后端类型。
     *
     * @return 后端类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    String backendType();

    /**
     * 返回可编译的计划类型。
     *
     * @return 计划类型集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Set<String> supportedPlanTypes();

    /**
     * 将计划编译为目标命令。
     *
     * @param plan 执行计划
     * @param context 查询上下文
     * @return 编译命令
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    CompiledCommand compile(ExecutionPlan plan, QueryContext context);
}
