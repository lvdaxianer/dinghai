package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Map;
import java.util.Objects;

/**
 * 后端执行阶段使用的服务端上下文。
 *
 * @param traceId 链路标识
 * @param dataSourceId 服务端解析后的数据源标识
 * @param attributes 由宿主应用注入的非敏感上下文属性
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record QueryContext(String traceId, String dataSourceId, Map<String, String> attributes) {

    /**
     * 校验并冻结查询上下文。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public QueryContext {
        traceId = requireText(traceId, "traceId");
        dataSourceId = requireText(dataSourceId, "dataSourceId");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }

    /**
     * 校验文本值。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
