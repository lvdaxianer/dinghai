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

    /**
     * 返回用于计划摘要和缓存键的结构化材料。
     *
     * <p>实现可以返回不含绑定参数值的 AST 或其他安全结构。默认使用元数据，兼容已有 DSL。</p>
     *
     * @return 计划结构摘要材料
     */
    default Object fingerprintMaterial() {
        return metadata();
    }
}
