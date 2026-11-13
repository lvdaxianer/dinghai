package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Map;

/**
 * 所有 DSL 编译结果共同实现的后端无关执行计划契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface ExecutionPlan {

    /**
     * 返回计划类型。
     *
     * @return 计划类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    String planType();

    /**
     * 返回计划版本。
     *
     * @return 计划版本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    int planVersion();

    /**
     * 返回不可变计划元数据。
     *
     * @return 计划元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Map<String, Object> metadata();
}
