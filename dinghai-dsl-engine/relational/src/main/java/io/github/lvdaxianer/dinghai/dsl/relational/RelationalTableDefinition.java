package io.github.lvdaxianer.dinghai.dsl.relational;

import java.util.Map;
import java.util.Objects;

/**
 * 已通过宿主白名单解析的关系表定义。
 *
 * @param logicalName 逻辑表名
 * @param physicalName 后端物理表名
 * @param columns 逻辑字段到物理字段的映射
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalTableDefinition(
        String logicalName,
        String physicalName,
        Map<String, RelationalColumnDefinition> columns) {

    /**
     * 校验并冻结表定义。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalTableDefinition {
        logicalName = requireText(logicalName, "logicalName");
        physicalName = requireText(physicalName, "physicalName");
        columns = Map.copyOf(Objects.requireNonNull(columns, "columns"));
    }

    /**
     * 校验文本字段。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本
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
