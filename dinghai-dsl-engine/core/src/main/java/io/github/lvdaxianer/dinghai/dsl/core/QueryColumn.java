package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Objects;

/**
 * 统一查询结果的列描述。
 *
 * @param name 输出列名
 * @param logicalType 逻辑类型
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record QueryColumn(String name, String logicalType) {

    /**
     * 校验列描述。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public QueryColumn {
        name = requireText(name, "name");
        logicalType = requireText(logicalType, "logicalType");
    }

    /**
     * 校验文本字段。
     *
     * @param value 字段值
     * @param name 字段名
     * @return 非空字段值
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
