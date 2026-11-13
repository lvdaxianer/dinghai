package io.github.lvdaxianer.dinghai.dsl.postgresql;

import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;

import java.util.Set;

/**
 * PostgreSQL 关系查询能力配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class PostgresqlCapabilityProfile {

    private PostgresqlCapabilityProfile() {
    }

    /**
     * 返回默认 PostgreSQL 能力快照。
     *
     * @return PostgreSQL 能力
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public static BackendCapabilities common() {
        return new BackendCapabilities("postgresql", Set.of(
                "relational-query",
                "inner-join",
                "left-join",
                "group-by",
                "order-by",
                "limit",
                "aggregate",
                "parameterized-read"));
    }
}
