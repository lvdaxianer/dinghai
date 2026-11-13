package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 不保存请求参数值的已编译命令模板。
 *
 * @param commandType 命令类型
 * @param commandBody 命令主体
 * @param parameterSchema 参数槽定义
 * @param outputColumns 输出列描述
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record CompiledCommandTemplate(
        String commandType,
        Object commandBody,
        List<ParameterTemplate> parameterSchema,
        List<QueryColumn> outputColumns) {

    /**
     * 校验并冻结编译模板。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public CompiledCommandTemplate {
        commandType = requireText(commandType, "commandType");
        commandBody = Objects.requireNonNull(commandBody, "commandBody");
        parameterSchema = List.copyOf(Objects.requireNonNull(parameterSchema, "parameterSchema"));
        outputColumns = List.copyOf(Objects.requireNonNull(outputColumns, "outputColumns"));
    }

    /**
     * 使用当前请求参数绑定命令模板。
     *
     * @param values 按占位符顺序排列的当前请求参数
     * @return 含当前参数值的执行命令
     */
    public CompiledCommand bind(List<?> values) {
        List<?> currentValues = List.copyOf(Objects.requireNonNull(values, "values"));
        if (currentValues.size() != parameterSchema.size()) {
            throw new IllegalArgumentException("parameter value count does not match compiled template");
        }
        List<ParameterBinding> bindings = new ArrayList<>(parameterSchema.size());
        for (int index = 0; index < parameterSchema.size(); index++) {
            ParameterTemplate parameter = parameterSchema.get(index);
            bindings.add(new ParameterBinding(parameter.name(), parameter.logicalType(), currentValues.get(index)));
        }
        return new CompiledCommand(commandType, commandBody, bindings, outputColumns);
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
