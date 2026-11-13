package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DiagnosticSeverity;
import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslDiagnostic;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.AggregateSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.FieldSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Join;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.JoinCondition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.LogicalPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Ordering;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ParameterReference;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Predicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 关系查询 AST 的语义校验器和执行计划构建器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class RelationalPlanBuilder {

    private final RelationalQueryParser parser;
    private final RelationalSchemaPolicy schemaPolicy;
    private final RelationalPlanLimits limits;

    /**
     * 使用默认复杂度限制创建计划构建器。
     *
     * @param schemaPolicy 表和字段白名单策略
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalPlanBuilder(RelationalSchemaPolicy schemaPolicy) {
        this(schemaPolicy, RelationalPlanLimits.defaults());
    }

    /**
     * 使用指定复杂度限制创建计划构建器。
     *
     * @param schemaPolicy 表和字段白名单策略
     * @param limits 复杂度限制
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalPlanBuilder(RelationalSchemaPolicy schemaPolicy, RelationalPlanLimits limits) {
        this.parser = new RelationalQueryParser();
        this.schemaPolicy = Objects.requireNonNull(schemaPolicy, "schemaPolicy");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * 解析、校验并构建后端无关关系查询计划。
     *
     * @param request DSL 请求
     * @param context DSL 编译上下文
     * @return 不可变关系查询计划
     * @throws DslCompilationException 语义、权限、参数或复杂度校验失败
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalQueryPlan build(DslRequest request, DslContext context) {
        DslRequest value = Objects.requireNonNull(request, "request");
        DslContext compileContext = Objects.requireNonNull(context, "context");
        RelationalQueryAst query = parser.parse(value);
        validateComplexity(query);
        ValidationState state = validateTables(query, compileContext);
        validateSelections(query, state);
        PredicateStats stats = validatePredicates(query.where(), state);
        validatePredicateLimits(stats);
        validateGroupBy(query.groupBy(), state);
        validateOrderBy(query.orderBy(), state);
        List<ParameterBinding> bindings = bindParameters(state, value.parameters());
        return new RelationalQueryPlan(
                query,
                state.tables,
                bindings,
                stats.depth(),
                metadata(query, stats));
    }

    /**
     * 校验静态复杂度边界。
     *
     * @param query 关系查询 AST
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private void validateComplexity(RelationalQueryAst query) {
        if (query.joins().size() > limits.maxJoins()
                || query.select().size() > limits.maxSelectItems()
                || query.limit() > limits.maxLimit()) {
            throw failure("QUERY_COMPLEXITY_EXCEEDED", "$.payload", "query exceeds configured limits");
        }
    }

    /**
     * 校验过滤条件的数量和嵌套深度。
     *
     * @param stats 条件统计
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private void validatePredicateLimits(PredicateStats stats) {
        if (stats.count() > limits.maxConditions() || stats.depth() > limits.maxDepth()) {
            throw failure("QUERY_COMPLEXITY_EXCEEDED", "$.payload.where", "predicate exceeds configured limits");
        }
    }

    /**
     * 解析来源表和 JOIN 表。
     *
     * @param query 关系查询 AST
     * @param context DSL 编译上下文
     * @return 别名解析状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private ValidationState validateTables(RelationalQueryAst query, DslContext context) {
        ValidationState state = new ValidationState();
        addTable(state, query.from().table(), query.from().alias(), "$.payload.from", context);
        for (int index = 0; index < query.joins().size(); index++) {
            validateJoin(query.joins().get(index), index, state, context);
        }
        return state;
    }

    /**
     * 解析并校验单个 JOIN。
     *
     * @param join JOIN AST
     * @param index JOIN 下标
     * @param state 校验状态
     * @param context DSL 编译上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private void validateJoin(Join join, int index, ValidationState state, DslContext context) {
        String path = "$.payload.joins[" + index + "]";
        addTable(state, join.table().table(), join.table().alias(), path + ".table", context);
        boolean crossScope = false;
        for (JoinCondition condition : join.on()) {
            resolveField(condition.left(), state, path + ".on.left");
            resolveField(condition.right(), state, path + ".on.right");
            crossScope |= !condition.left().qualifier().equals(condition.right().qualifier())
                    && (condition.left().qualifier().equals(join.table().alias())
                    || condition.right().qualifier().equals(join.table().alias()));
        }
        if (!crossScope) {
            throw failure("JOIN_ON_CROSS_SCOPE", path + ".on", "JOIN ON must reference the joined table");
        }
    }

    /**
     * 加入已解析表并拒绝别名冲突。
     *
     * @param state 校验状态
     * @param logicalName 逻辑表名
     * @param alias 表别名
     * @param path 字段路径
     * @param context DSL 编译上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private void addTable(
            ValidationState state,
            String logicalName,
            String alias,
            String path,
            DslContext context) {
        if (state.tables.containsKey(alias)) {
            throw failure("ALIAS_DUPLICATE", path + ".alias", "table alias is duplicated");
        }
        Optional<RelationalTableDefinition> resolved = schemaPolicy.resolveTable(logicalName, context);
        if (resolved == null || resolved.isEmpty()) {
            throw failure("TABLE_NOT_ALLOWED", path + ".table", "table is not allowed");
        }
        state.tables.put(alias, resolved.get());
    }

    /**
     * 校验投影字段和聚合字段。
     *
     * @param query 关系查询 AST
     * @param state 校验状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void validateSelections(RelationalQueryAst query, ValidationState state) {
        for (SelectionWithIndex selection : SelectionWithIndex.of(query.select())) {
            if (selection.value() instanceof FieldSelection field) {
                resolveField(field.field(), state, "$.payload.select[" + selection.index() + "]");
            } else if (selection.value() instanceof AggregateSelection aggregate) {
                resolveField(aggregate.field(), state, "$.payload.select[" + selection.index() + "]");
            }
        }
    }

    /**
     * 递归校验过滤条件并收集参数引用。
     *
     * @param predicates 过滤条件
     * @param state 校验状态
     * @return 条件统计
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static PredicateStats validatePredicates(List<Predicate> predicates, ValidationState state) {
        PredicateStats total = new PredicateStats(0, 0);
        for (Predicate predicate : predicates) {
            PredicateStats current = validatePredicate(predicate, state);
            total = total.combine(current);
        }
        return total;
    }

    /**
     * 校验单个过滤条件。
     *
     * @param predicate 过滤条件
     * @param state 校验状态
     * @return 条件统计
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static PredicateStats validatePredicate(Predicate predicate, ValidationState state) {
        if (predicate instanceof ComparisonPredicate comparison) {
            resolveField(comparison.field(), state, "$.payload.where.field");
            registerParameter(state, comparison.parameter(), comparison.operator());
            return new PredicateStats(1, 1);
        }
        LogicalPredicate logical = (LogicalPredicate) predicate;
        PredicateStats children = validatePredicates(logical.predicates(), state);
        return new PredicateStats(children.count(), children.depth() + 1);
    }

    /**
     * 校验分组字段。
     *
     * @param fields 分组字段
     * @param state 校验状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void validateGroupBy(List<RelationalField> fields, ValidationState state) {
        for (RelationalField field : fields) {
            resolveField(field, state, "$.payload.groupBy");
        }
    }

    /**
     * 校验排序字段。
     *
     * @param orderings 排序项
     * @param state 校验状态
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void validateOrderBy(List<Ordering> orderings, ValidationState state) {
        for (Ordering ordering : orderings) {
            resolveField(ordering.field(), state, "$.payload.orderBy");
        }
    }

    /**
     * 解析字段并校验列白名单。
     *
     * @param field 字段引用
     * @param state 校验状态
     * @param path 字段路径
     * @return 字段定义
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static RelationalColumnDefinition resolveField(
            RelationalField field, ValidationState state, String path) {
        RelationalTableDefinition table = state.tables.get(field.qualifier());
        if (table == null) {
            throw failure("FIELD_ALIAS_NOT_ALLOWED", path, "field alias is not allowed");
        }
        RelationalColumnDefinition column = table.columns().get(field.name());
        if (column == null) {
            throw failure("FIELD_NOT_ALLOWED", path, "field is not allowed");
        }
        return column;
    }

    /**
     * 记录参数引用并校验同名参数类型一致。
     *
     * @param state 校验状态
     * @param parameter 参数引用
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void registerParameter(
            ValidationState state, ParameterReference parameter, ComparisonOperator operator) {
        ParameterReference previous = state.parameters.putIfAbsent(parameter.name(), parameter);
        if (previous != null && !previous.logicalType().equals(parameter.logicalType())) {
            throw failure("PARAMETER_TYPE_CONFLICT", "$.payload.where", "parameter logical type is inconsistent");
        }
        state.operators.putIfAbsent(parameter.name(), operator);
    }

    /**
     * 校验并构造参数绑定。
     *
     * @param state 校验状态
     * @param values 请求参数
     * @return 参数绑定列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<ParameterBinding> bindParameters(
            ValidationState state, Map<String, Object> values) {
        Map<String, Object> supplied = Objects.requireNonNull(values, "parameters");
        List<ParameterBinding> bindings = new ArrayList<>(state.parameters.size());
        for (ParameterReference parameter : state.parameters.values()) {
            if (!supplied.containsKey(parameter.name())) {
                throw failure("PARAMETER_MISSING", "$.parameters." + parameter.name(), "parameter is missing");
            }
            Object value = supplied.get(parameter.name());
            validateParameterValue(parameter, state.operators.get(parameter.name()), value);
            bindings.add(new ParameterBinding(parameter.name(), parameter.logicalType(), value));
        }
        for (String name : supplied.keySet()) {
            if (!state.parameters.containsKey(name)) {
                throw failure("PARAMETER_UNUSED", "$.parameters." + name, "parameter is not referenced by the DSL");
            }
        }
        return List.copyOf(bindings);
    }

    /**
     * 校验参数值类型和集合边界。
     *
     * @param parameter 参数定义
     * @param value 参数值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void validateParameterValue(
            ParameterReference parameter, ComparisonOperator operator, Object value) {
        if (value == null) {
            throw failure("PARAMETER_NULL", "$.parameters." + parameter.name(), "parameter value is null");
        }
        if (ComparisonOperator.IN == operator && !(value instanceof Collection<?>)) {
            throw failure("PARAMETER_TYPE_INVALID", "$.parameters." + parameter.name(), "IN parameter must be a collection");
        }
        if (ComparisonOperator.IN != operator && value instanceof Collection<?>) {
            throw failure("PARAMETER_TYPE_INVALID", "$.parameters." + parameter.name(), "scalar parameter must not be a collection");
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            throw failure("PARAMETER_EMPTY", "$.parameters." + parameter.name(), "parameter collection must not be empty");
        }
        if (!compatible(parameter.logicalType(), value)) {
            throw failure("PARAMETER_TYPE_INVALID", "$.parameters." + parameter.name(), "parameter type is invalid");
        }
    }

    /**
     * 判断参数值是否符合逻辑类型。
     *
     * @param logicalType 逻辑类型
     * @param value 参数值
     * @return 是否兼容
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static boolean compatible(String logicalType, Object value) {
        String type = logicalType.toLowerCase(Locale.ROOT);
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(item -> compatibleScalar(type, item));
        }
        return compatibleScalar(type, value);
    }

    /**
     * 判断标量参数类型。
     *
     * @param logicalType 逻辑类型
     * @param value 参数值
     * @return 是否兼容
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static boolean compatibleScalar(String logicalType, Object value) {
        return switch (logicalType) {
            case "string" -> value instanceof CharSequence;
            case "long" -> value instanceof Long || value instanceof Integer
                    || value instanceof Short || value instanceof Byte;
            case "integer" -> value instanceof Integer || value instanceof Short || value instanceof Byte;
            case "decimal" -> value instanceof BigDecimal || value instanceof Double || value instanceof Float;
            case "boolean" -> value instanceof Boolean;
            case "date" -> value instanceof LocalDate || value instanceof CharSequence;
            case "datetime" -> value instanceof LocalDateTime || value instanceof CharSequence;
            default -> false;
        };
    }

    /**
     * 生成不含参数值的计划元数据。
     *
     * @param query 查询 AST
     * @param stats 条件统计
     * @return 计划元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Map<String, Object> metadata(RelationalQueryAst query, PredicateStats stats) {
        return Map.of(
                "dslType", RelationalQueryParser.DSL_TYPE,
                "dslVersion", RelationalQueryParser.VERSION,
                "joinCount", query.joins().size(),
                "selectCount", query.select().size(),
                "conditionCount", stats.count(),
                "predicateDepth", stats.depth());
    }

    /**
     * 构造安全诊断异常。
     *
     * @param code 稳定错误码
     * @param path DSL 路径
     * @param message 脱敏消息
     * @return 编译异常
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static DslCompilationException failure(String code, String path, String message) {
        return new DslCompilationException(List.of(
                new DslDiagnostic(DiagnosticSeverity.ERROR, code, path, message)));
    }

    /**
     * 校验状态。
     */
    private static final class ValidationState {

        private final Map<String, RelationalTableDefinition> tables = new LinkedHashMap<>();
        private final Map<String, ParameterReference> parameters = new LinkedHashMap<>();
        private final Map<String, ComparisonOperator> operators = new LinkedHashMap<>();
    }

    /**
     * 条件统计。
     *
     * @param count 条件数量
     * @param depth 条件深度
     */
    private record PredicateStats(int count, int depth) {

        /**
         * 合并条件统计。
         *
         * @param other 其他统计
         * @return 合并统计
         */
        private PredicateStats combine(PredicateStats other) {
            return new PredicateStats(count + other.count, Math.max(depth, other.depth));
        }
    }

    /**
     * 带下标的投影项。
     *
     * @param value 投影项
     * @param index 下标
     */
    private record SelectionWithIndex(io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Selection value,
                                      int index) {

        /**
         * 构造带下标投影列表。
         *
         * @param values 投影列表
         * @return 带下标列表
         */
        private static List<SelectionWithIndex> of(List<io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Selection> values) {
            List<SelectionWithIndex> result = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                result.add(new SelectionWithIndex(values.get(index), index));
            }
            return result;
        }
    }
}
