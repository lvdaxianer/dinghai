package io.github.lvdaxianer.dinghai.dsl.postgresql;

/**
 * PostgreSQL JDBC 执行资源限制。
 *
 * @param queryTimeoutSeconds JDBC 查询超时秒数
 * @param maxRows 最大返回行数
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record PostgresqlExecutionLimits(int queryTimeoutSeconds, int maxRows) {

    /**
     * 校验执行限制。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public PostgresqlExecutionLimits {
        if (queryTimeoutSeconds < 1 || maxRows < 1) {
            throw new IllegalArgumentException("PostgreSQL execution limits must be positive");
        }
    }

    /**
     * 返回安全默认执行限制。
     *
     * @return 默认限制
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public static PostgresqlExecutionLimits defaults() {
        return new PostgresqlExecutionLimits(30, 10_000);
    }
}
