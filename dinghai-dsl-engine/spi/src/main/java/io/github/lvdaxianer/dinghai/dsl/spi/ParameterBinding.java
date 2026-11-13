package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 已编译命令的类型化参数绑定。
 *
 * @param name 参数名
 * @param logicalType 逻辑类型
 * @param value 参数值
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record ParameterBinding(String name, String logicalType, Object value) {

    /**
     * 校验参数绑定。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public ParameterBinding {
        name = requireText(name, "name");
        logicalType = requireText(logicalType, "logicalType");
    }

    /**
     * 校验文本字段。
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
