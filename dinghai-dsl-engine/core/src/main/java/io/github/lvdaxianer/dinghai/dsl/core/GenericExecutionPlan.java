package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Map;
import java.util.Objects;

/**
 * 供扩展 DSL 使用的最小不可变执行计划实现。
 *
 * @param planType 计划类型
 * @param planVersion 计划版本
 * @param metadata 计划元数据
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record GenericExecutionPlan(
        String planType,
        int planVersion,
        Map<String, Object> metadata) implements ExecutionPlan {

    /**
     * 校验并冻结计划。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public GenericExecutionPlan {
        planType = requirePlanType(planType);
        // 计划版本必须有效，避免后端误执行未知计划格式。
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion must be positive");
        }
        metadata = ImmutableValues.copyMap(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * 校验计划类型。
     *
     * @param value 计划类型
     * @return 非空计划类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requirePlanType(String value) {
        String normalized = Objects.requireNonNull(value, "planType").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("planType must not be blank");
        }
        return normalized;
    }
}
