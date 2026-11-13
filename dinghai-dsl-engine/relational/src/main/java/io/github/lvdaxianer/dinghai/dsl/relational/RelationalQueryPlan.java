package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * relational-query v1 生成的后端无关执行计划。
 *
 * @param query 类型化查询 AST
 * @param tablesByAlias 已解析的别名和白名单表定义
 * @param parameters 已校验参数绑定
 * @param predicateDepth 条件深度
 * @param metadata 计划元数据
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalQueryPlan(
        RelationalQueryAst query,
        Map<String, RelationalTableDefinition> tablesByAlias,
        List<ParameterBinding> parameters,
        int predicateDepth,
        Map<String, Object> metadata) implements ExecutionPlan {

    /**
     * 校验并冻结关系查询计划。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalQueryPlan {
        query = Objects.requireNonNull(query, "query");
        tablesByAlias = Map.copyOf(Objects.requireNonNull(tablesByAlias, "tablesByAlias"));
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (predicateDepth < 0) {
            throw new IllegalArgumentException("predicateDepth must not be negative");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * 返回计划类型。
     *
     * @return 关系查询计划类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public String planType() {
        return RelationalQueryParser.DSL_TYPE;
    }

    /**
     * 返回计划版本。
     *
     * @return 关系查询计划版本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public int planVersion() {
        return RelationalQueryParser.VERSION;
    }
}
