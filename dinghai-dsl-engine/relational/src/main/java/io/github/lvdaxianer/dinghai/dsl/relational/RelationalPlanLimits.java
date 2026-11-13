package io.github.lvdaxianer.dinghai.dsl.relational;

/**
 * 关系查询计划复杂度限制。
 *
 * @param maxJoins 最大 JOIN 数量
 * @param maxSelectItems 最大投影项数量
 * @param maxConditions 最大条件数量
 * @param maxDepth 最大条件嵌套深度
 * @param maxLimit 最大返回行数
 * @param maxParameters 最大参数数量
 * @param maxInItems 单个 IN 参数允许的最大元素数量
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalPlanLimits(
        int maxJoins,
        int maxSelectItems,
        int maxConditions,
        int maxDepth,
        int maxLimit,
        int maxParameters,
        int maxInItems) {

    /**
     * 兼容旧版五参数构造方式，并使用安全参数默认值。
     *
     * @param maxJoins 最大 JOIN 数量
     * @param maxSelectItems 最大投影项数量
     * @param maxConditions 最大条件数量
     * @param maxDepth 最大条件嵌套深度
     * @param maxLimit 最大返回行数
     */
    public RelationalPlanLimits(
            int maxJoins, int maxSelectItems, int maxConditions, int maxDepth, int maxLimit) {
        this(maxJoins, maxSelectItems, maxConditions, maxDepth, maxLimit, 256, 1_000);
    }

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
        requirePositive(maxParameters, "maxParameters");
        requirePositive(maxInItems, "maxInItems");
    }

    /**
     * 返回安全默认限制。
     *
     * @return 默认限制
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public static RelationalPlanLimits defaults() {
        return new RelationalPlanLimits(8, 64, 128, 16, 10_000, 256, 1_000);
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
