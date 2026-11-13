package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;

import java.util.List;
import java.util.Objects;

/**
 * 不绑定具体协议的已编译命令。
 *
 * @param commandType 命令类型
 * @param commandBody 目标系统命令主体
 * @param parameters 参数绑定列表
 * @param outputColumns 输出列描述
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record CompiledCommand(
        String commandType,
        Object commandBody,
        List<ParameterBinding> parameters,
        List<QueryColumn> outputColumns) {

    /**
     * 校验并冻结编译命令。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public CompiledCommand {
        commandType = requireText(commandType, "commandType");
        commandBody = Objects.requireNonNull(commandBody, "commandBody");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        outputColumns = List.copyOf(Objects.requireNonNull(outputColumns, "outputColumns"));
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
