package io.github.lvdaxianer.dinghai.dsl.mysql;

import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;

import java.util.Set;

/**
 * MySQL 与 Doris MySQL 协议的公共能力配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class MysqlCapabilityProfile {

    private MysqlCapabilityProfile() {
    }

    /**
     * 返回默认公共能力快照。
     *
     * @return MySQL/Doris 公共能力
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public static BackendCapabilities common() {
        return new BackendCapabilities("mysql", Set.of(
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
