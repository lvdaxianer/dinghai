package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;

/**
 * 执行已编译后端命令的契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface PlanExecutor {

    /**
     * 返回执行器目标后端类型。
     *
     * @return 后端类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    String backendType();

    /**
     * 执行后端命令。
     *
     * @param command 已编译命令
     * @param context 查询上下文
     * @return 统一查询结果
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    QueryResult execute(CompiledCommand command, QueryContext context);
}
