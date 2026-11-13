package io.github.lvdaxianer.dinghai.dsl.relational;

/**
 * 关系查询计划复杂度限制。
 *
 * @param maxJoins 最大 JOIN 数量
 * @param maxSelectItems 最大投影项数量
 * @param maxConditions 最大条件数量
 * @param maxDepth 最大条件嵌套深度
 * @param maxLimit 最大返回行数
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalPlanLimits(
        int maxJoins,
        int maxSelectItems,
        int maxConditions,
        int maxDepth,
        int maxLimit) {

    /**
     * 校验限制值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalPlanLimits {
        requirePositive(maxJoins, "maxJoins");
        requirePositive(maxSelectItems, "maxSelectItems");
        requirePositive(maxConditions, "maxConditions");
        requirePositive(maxDepth, "maxDepth");
        requirePositive(maxLimit, "maxLimit");
    }

    /**
     * 返回安全默认限制。
     *
     * @return 默认限制
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public static RelationalPlanLimits defaults() {
        return new RelationalPlanLimits(8, 64, 128, 16, 10_000);
    }

    /**
     * 校验正数限制。
     *
     * @param value 限制值
     * @param name 字段名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
