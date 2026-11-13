package io.github.lvdaxianer.dinghai.dsl.relational;

/**
 * 关系约束在最终查询中的安全放置位置。
 */
public enum ConstraintPlacement {

    /**
     * 放入查询 WHERE。
     */
    WHERE,

    /**
     * 放入目标 JOIN 的 ON。
     */
    JOIN_ON
}
