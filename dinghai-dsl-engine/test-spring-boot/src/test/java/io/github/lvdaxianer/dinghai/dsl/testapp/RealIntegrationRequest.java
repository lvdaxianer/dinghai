package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;

import java.util.List;
import java.util.Map;

/**
 * 真实数据库集成测试使用的结构化查询，不包含 SQL。
 */
final class RealIntegrationRequest {

    private RealIntegrationRequest() {
    }

    static DslRequest activeOpenOutages() {
        return new DslRequest(
                "relational-query",
                1,
                Map.of(
                        "from", Map.of("table", "fact_wireless_outage_timeline", "alias", "f"),
                        "select", List.of(
                                Map.of("field", "f.event_date"),
                                Map.of("field", "a.asset_name")),
                        "joins", List.of(Map.of(
                                "type", "left",
                                "table", Map.of("table", "outage_asset", "alias", "a"),
                                "on", List.of(Map.of("left", "f.asset_id", "right", "a.asset_id")))),
                        "where", List.of(Map.of(
                                "field", "f.status",
                                "operator", "eq",
                                "parameter", "status",
                                "logicalType", "string")),
                        "groupBy", List.of(),
                        "orderBy", List.of(),
                        "limit", 10),
                Map.of("status", "OPEN"));
    }
}
