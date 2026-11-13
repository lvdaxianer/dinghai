package io.github.lvdaxianer.dinghai.dsl.mysql;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalColumnDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryPlan;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalTableDefinition;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCompilationException;
import io.github.lvdaxianer.dinghai.dsl.spi.CompiledCommand;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanCompiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 生成 MySQL 与 Doris MySQL 协议公共 SQL 的关系计划编译器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class MysqlRelationalCompiler implements PlanCompiler {

    /**
     * 返回目标后端类型。
     *
     * @return mysql
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public String backendType() {
        return "mysql";
    }

    /**
     * 返回支持的计划类型。
     *
     * @return 关系查询计划类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public Set<String> supportedPlanTypes() {
        return Set.of("relational-query");
    }

    /**
     * 将关系计划编译为参数化 MySQL 命令。
     *
     * @param plan 后端无关计划
     * @param context 查询上下文
     * @return SQL 命令和参数绑定
     * @throws BackendCompilationException 计划不支持或物理标识符非法
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public CompiledCommand compile(ExecutionPlan plan, QueryContext context) {
        RelationalQueryPlan relational = requirePlan(plan);
        Objects.requireNonNull(context, "context");
        CompilationState state = new CompilationState(relational);
        appendSelect(state);
        appendFromAndJoins(state);
        appendWhere(state);
        appendGroupBy(state);
        appendOrderBy(state);
        state.sql.append(" LIMIT ").append(relational.query().limit());
        return new CompiledCommand("sql", state.sql.toString(), state.bindings, state.columns);
    }

    /**
     * 校验计划类型并转换为关系计划。
     *
     * @param plan 执行计划
     * @return 关系计划
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static RelationalQueryPlan requirePlan(ExecutionPlan plan) {
        if (!(plan instanceof RelationalQueryPlan relational)) {
            throw new BackendCompilationException("PLAN_TYPE_NOT_SUPPORTED", "MySQL compiler requires relational-query plan");
        }
        return relational;
    }

    /**
     * 追加 SELECT 投影。
     *
     * @param state 编译状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendSelect(CompilationState state) {
        state.sql.append("SELECT ");
        List<RelationalQueryAst.Selection> selections = state.plan.query().select();
        for (int index = 0; index < selections.size(); index++) {
            if (index > 0) {
                state.sql.append(", ");
            }
            appendSelection(state, selections.get(index));
        }
    }

    /**
     * 追加单个投影。
     *
     * @param state 编译状态
     * @param selection 投影项
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendSelection(CompilationState state, RelationalQueryAst.Selection selection) {
        if (selection instanceof RelationalQueryAst.FieldSelection field) {
            appendField(state, field.field());
            String label = label(field.alias(), field.field());
            appendAlias(state.sql, label);
            state.columns.add(new QueryColumn(label, column(state, field.field()).logicalType()));
            return;
        }
        RelationalQueryAst.AggregateSelection aggregate = (RelationalQueryAst.AggregateSelection) selection;
        state.sql.append(aggregate.function().name()).append('(');
        appendField(state, aggregate.field());
        state.sql.append(')');
        String label = label(aggregate.alias(), aggregate.field(), aggregate.function());
        appendAlias(state.sql, label);
        state.columns.add(new QueryColumn(label, aggregateType(aggregate, column(state, aggregate.field()))));
    }

    /**
     * 追加 FROM 和 JOIN。
     *
     * @param state 编译状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendFromAndJoins(CompilationState state) {
        RelationalQueryAst query = state.plan.query();
        state.sql.append(" FROM ");
        appendTable(state, query.from());
        for (RelationalQueryAst.Join join : query.joins()) {
            state.sql.append(' ').append(join.type().name()).append(" JOIN ");
            appendTable(state, join.table());
            state.sql.append(" ON ");
            appendJoinConditions(state, join.on());
            if (!join.additionalOn().isEmpty()) {
                state.sql.append(" AND ");
                appendPredicates(state, join.additionalOn(), " AND ");
            }
        }
    }

    /**
     * 追加表引用。
     *
     * @param state 编译状态
     * @param table 表来源
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendTable(CompilationState state, RelationalQueryAst.TableSource table) {
        RelationalTableDefinition definition = state.plan.tablesByAlias().get(table.alias());
        state.sql.append(quote(definition.physicalName())).append(" AS ").append(quote(table.alias()));
    }

    /**
     * 追加 JOIN ON 条件。
     *
     * @param state 编译状态
     * @param conditions ON 条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendJoinConditions(CompilationState state, List<RelationalQueryAst.JoinCondition> conditions) {
        for (int index = 0; index < conditions.size(); index++) {
            if (index > 0) {
                state.sql.append(" AND ");
            }
            RelationalQueryAst.JoinCondition condition = conditions.get(index);
            appendField(state, condition.left());
            state.sql.append(" = ");
            appendField(state, condition.right());
        }
    }

    /**
     * 追加 WHERE 条件。
     *
     * @param state 编译状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendWhere(CompilationState state) {
        if (state.plan.query().where().isEmpty()) {
            return;
        }
        state.sql.append(" WHERE ");
        appendPredicates(state, state.plan.query().where(), " AND ");
    }

    /**
     * 追加条件列表。
     *
     * @param state 编译状态
     * @param predicates 条件列表
     * @param separator 条件连接符
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendPredicates(
            CompilationState state, List<RelationalQueryAst.Predicate> predicates, String separator) {
        for (int index = 0; index < predicates.size(); index++) {
            if (index > 0) {
                state.sql.append(separator);
            }
            appendPredicate(state, predicates.get(index));
        }
    }

    /**
     * 追加单个条件。
     *
     * @param state 编译状态
     * @param predicate 条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendPredicate(CompilationState state, RelationalQueryAst.Predicate predicate) {
        if (predicate instanceof RelationalQueryAst.ComparisonPredicate comparison) {
            appendComparison(state, comparison);
            return;
        }
        RelationalQueryAst.LogicalPredicate logical = (RelationalQueryAst.LogicalPredicate) predicate;
        state.sql.append('(');
        appendPredicates(state, logical.predicates(), " " + logical.operator().name() + " ");
        state.sql.append(')');
    }

    /**
     * 追加字段比较条件。
     *
     * @param state 编译状态
     * @param comparison 比较条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendComparison(
            CompilationState state, RelationalQueryAst.ComparisonPredicate comparison) {
        appendField(state, comparison.field());
        if (comparison.operator() == RelationalQueryAst.ComparisonOperator.IN) {
            appendInParameter(state, comparison.parameter().name());
            return;
        }
        state.sql.append(' ').append(operator(comparison.operator())).append(" ?");
        appendBinding(state, comparison.parameter().name());
    }

    /**
     * 追加 IN 集合占位符。
     *
     * @param state 编译状态
     * @param parameterName 参数名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendInParameter(CompilationState state, String parameterName) {
        ParameterBinding binding = state.parametersByName.get(parameterName);
        if (!(binding.value() instanceof Collection<?> values) || values.isEmpty()) {
            throw new BackendCompilationException("PARAMETER_BINDING_INVALID", "IN parameter must be a non-empty collection");
        }
        state.sql.append(" IN (");
        int index = 0;
        for (Object value : values) {
            if (index++ > 0) {
                state.sql.append(", ");
            }
            state.sql.append('?');
            state.bindings.add(new ParameterBinding(
                    parameterName + "_" + (index - 1), binding.logicalType(), value));
        }
        state.sql.append(')');
    }

    /**
     * 追加普通参数绑定。
     *
     * @param state 编译状态
     * @param parameterName 参数名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendBinding(CompilationState state, String parameterName) {
        ParameterBinding binding = state.parametersByName.get(parameterName);
        if (binding == null) {
            throw new BackendCompilationException("PARAMETER_BINDING_MISSING", "compiled parameter is missing");
        }
        state.bindings.add(binding);
    }

    /**
     * 追加 GROUP BY。
     *
     * @param state 编译状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendGroupBy(CompilationState state) {
        if (state.plan.query().groupBy().isEmpty()) {
            return;
        }
        state.sql.append(" GROUP BY ");
        for (int index = 0; index < state.plan.query().groupBy().size(); index++) {
            if (index > 0) {
                state.sql.append(", ");
            }
            appendField(state, state.plan.query().groupBy().get(index));
        }
    }

    /**
     * 追加 ORDER BY。
     *
     * @param state 编译状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendOrderBy(CompilationState state) {
        if (state.plan.query().orderBy().isEmpty()) {
            return;
        }
        state.sql.append(" ORDER BY ");
        for (int index = 0; index < state.plan.query().orderBy().size(); index++) {
            if (index > 0) {
                state.sql.append(", ");
            }
            RelationalQueryAst.Ordering ordering = state.plan.query().orderBy().get(index);
            appendField(state, ordering.field());
            state.sql.append(' ').append(ordering.direction().name());
        }
    }

    /**
     * 追加限定字段。
     *
     * @param state 编译状态
     * @param field 字段引用
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendField(CompilationState state, RelationalQueryAst.RelationalField field) {
        RelationalColumnDefinition column = column(state, field);
        state.sql.append(quote(field.qualifier())).append('.').append(quote(column.physicalName()));
    }

    /**
     * 获取已解析字段定义。
     *
     * @param state 编译状态
     * @param field 字段引用
     * @return 字段定义
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static RelationalColumnDefinition column(
            CompilationState state, RelationalQueryAst.RelationalField field) {
        RelationalTableDefinition table = state.plan.tablesByAlias().get(field.qualifier());
        if (table == null || table.columns().get(field.name()) == null) {
            throw new BackendCompilationException("FIELD_NOT_RESOLVED", "compiled field is not resolved");
        }
        return table.columns().get(field.name());
    }

    /**
     * 生成安全输出列名。
     *
     * @param alias 显式别名
     * @param field 字段
     * @return 输出列名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String label(String alias, RelationalQueryAst.RelationalField field) {
        return alias == null ? field.qualifier() + "_" + field.name() : alias;
    }

    /**
     * 生成聚合输出列名。
     *
     * @param alias 显式别名
     * @param field 字段
     * @param function 聚合函数
     * @return 输出列名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String label(
            String alias, RelationalQueryAst.RelationalField field, RelationalQueryAst.AggregateFunction function) {
        return alias == null ? function.name().toLowerCase() + "_" + field.name() : alias;
    }

    /**
     * 生成聚合逻辑类型。
     *
     * @param aggregate 聚合项
     * @param source 字段类型
     * @return 逻辑类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String aggregateType(
            RelationalQueryAst.AggregateSelection aggregate, RelationalColumnDefinition source) {
        return aggregate.function() == RelationalQueryAst.AggregateFunction.COUNT
                ? "long" : source.logicalType();
    }

    /**
     * 追加输出别名。
     *
     * @param sql SQL 构建器
     * @param alias 输出别名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void appendAlias(StringBuilder sql, String alias) {
        sql.append(" AS ").append(quote(alias));
    }

    /**
     * 转换比较运算符。
     *
     * @param operator AST 运算符
     * @return SQL 运算符
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String operator(RelationalQueryAst.ComparisonOperator operator) {
        return switch (operator) {
            case EQ -> "=";
            case NOT_EQ -> "<>";
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            case LIKE -> "LIKE";
            case IN -> "IN";
        };
    }

    /**
     * 引用并校验 SQL 标识符。
     *
     * @param value 标识符
     * @return 反引号标识符
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String quote(String value) {
        if (!isIdentifier(value)) {
            throw new BackendCompilationException("INVALID_IDENTIFIER", "physical identifier is not allowed");
        }
        return "`" + value + "`";
    }

    /**
     * 判断是否为普通标识符。
     *
     * @param value 标识符
     * @return 是否安全
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty()
                || !(Character.isLetter(value.charAt(0)) || value.charAt(0) == '_')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(Character.isLetterOrDigit(current) || current == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 编译过程中的可变状态。
     */
    private static final class CompilationState {

        private final RelationalQueryPlan plan;
        private final StringBuilder sql = new StringBuilder();
        private final Map<String, ParameterBinding> parametersByName = new LinkedHashMap<>();
        private final List<ParameterBinding> bindings = new ArrayList<>();
        private final List<QueryColumn> columns = new ArrayList<>();

        private CompilationState(RelationalQueryPlan plan) {
            this.plan = plan;
            for (ParameterBinding binding : plan.parameters()) {
                parametersByName.put(binding.name(), binding);
            }
        }
    }
}
