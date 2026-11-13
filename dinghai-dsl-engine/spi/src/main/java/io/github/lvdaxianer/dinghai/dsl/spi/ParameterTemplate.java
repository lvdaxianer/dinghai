package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 不包含实际值的编译参数模板。
 *
 * @param name 参数槽名称
 * @param logicalType 参数逻辑类型
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record ParameterTemplate(String name, String logicalType) {

    /**
     * 校验参数模板。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public ParameterTemplate {
        name = requireText(name, "name");
        logicalType = requireText(logicalType, "logicalType");
    }

    /**
     * 转换为只保留类型信息的绑定。
     *
     * @return 不包含实际值的参数绑定
     */
    public ParameterBinding emptyBinding() {
        return new ParameterBinding(name, logicalType, null);
    }

    /**
     * 校验文本字段。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本
     */
    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
