package io.github.lvdaxianer.dinghai.dsl.relational;

import java.util.Objects;

/**
 * 已通过宿主白名单解析的关系字段定义。
 *
 * @param logicalName 逻辑字段名
 * @param physicalName 后端物理字段名
 * @param logicalType 逻辑类型
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalColumnDefinition(String logicalName, String physicalName, String logicalType) {

    /**
     * 校验字段定义。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalColumnDefinition {
        logicalName = requireText(logicalName, "logicalName");
        physicalName = requireText(physicalName, "physicalName");
        logicalType = requireText(logicalType, "logicalType");
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
