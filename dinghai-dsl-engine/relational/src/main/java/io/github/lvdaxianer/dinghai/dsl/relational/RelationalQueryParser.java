package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DiagnosticSeverity;
import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.core.DslDiagnostic;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.AggregateFunction;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.AggregateSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.FieldSelection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Join;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.JoinCondition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.JoinType;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.LogicalOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.LogicalPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.OrderDirection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Ordering;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ParameterReference;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Predicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Selection;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.TableSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * relational-query v1 的严格结构解析器。
 *
 * <p>解析器只接受结构化字段，不接受 SQL 文本、SQL 片段或未注册表达式。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class RelationalQueryParser {

    /** relational-query DSL 类型。 */
    public static final String DSL_TYPE = "relational-query";

    /** relational-query 当前版本。 */
    public static final int VERSION = 1;

    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 256;
    private static final int MAX_SELECT_ITEMS = 64;
    private static final int MAX_JOINS = 8;
    private static final int MAX_CONDITIONS = 128;
    private static final int MAX_ORDER_ITEMS = 32;

    /**
     * 解析统一 DSL 请求。
     *
     * @param request DSL 请求
     * @return 类型化关系查询 AST
     * @throws DslCompilationException 请求类型、版本或结构非法
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalQueryAst parse(DslRequest request) {
        DslRequest value = Objects.requireNonNull(request, "request");
        if (!DSL_TYPE.equals(value.dslType())) {
            throw failure("DSL_TYPE_NOT_SUPPORTED", "$.dslType", "unsupported DSL type");
        }
        if (VERSION != value.version()) {
            throw failure("DSL_VERSION_NOT_SUPPORTED", "$.version", "unsupported DSL version");
        }
        return parsePayload(value.payload());
    }

    /**
     * 解析关系查询载荷。
     *
     * @param payload 结构化关系查询载荷
     * @return 类型化关系查询 AST
     * @throws DslCompilationException 载荷结构非法
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalQueryAst parsePayload(Map<String, Object> payload) {
        Map<String, Object> root = requireMap(payload, "$");
        checkShape(root, "$");
        requireKeys(root, Set.of("from", "select", "joins", "where", "groupBy", "orderBy", "limit"), "$");
        TableSource from = parseTable(root, "from", "$.from");
        List<Selection> select = parseSelect(root, "$.select");
        List<Join> joins = parseJoins(root.get("joins"), "$.joins");
        List<Predicate> where = parseWhere(root.get("where"), "$.where");
        List<RelationalField> groupBy = parseGroupBy(root.get("groupBy"), "$.groupBy");
        List<Ordering> orderBy = parseOrderBy(root.get("orderBy"), "$.orderBy");
        int limit = readRequiredInt(root, "limit", "$.limit");
        return new RelationalQueryAst(from, select, joins, where, groupBy, orderBy, limit);
    }

    /**
     * 解析投影列表。
     *
     * @param root 根对象
     * @param path 字段路径
     * @return 投影项
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Selection> parseSelect(Map<String, Object> root, String path) {
        List<?> values = readRequiredList(root, "select", path);
        if (values.isEmpty() || values.size() > MAX_SELECT_ITEMS) {
            throw failure("SELECT_SIZE_EXCEEDED", path, "select item count is outside the allowed range");
        }
        List<Selection> selections = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            selections.add(parseSelection(values.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(selections);
    }

    /**
     * 解析单个投影项。
     *
     * @param value 原始投影对象
     * @param path 字段路径
     * @return 类型化投影项
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Selection parseSelection(Object value, String path) {
        Map<String, Object> item = requireMap(value, path);
        requireKeys(item, Set.of("field", "aggregate", "alias"), path);
        boolean hasField = item.containsKey("field");
        boolean hasAggregate = item.containsKey("aggregate");
        if (!hasField && !hasAggregate) {
            throw failure("SELECTION_SHAPE_INVALID", path, "field or aggregate is required");
        }
        RelationalField field = parseField(item, "field", path + ".field");
        String alias = readOptionalString(item, "alias", path + ".alias");
        if (alias != null) {
            alias = parseIdentifier(alias, path + ".alias");
        }
        if (!hasAggregate) {
            return new FieldSelection(field, alias);
        }
        AggregateFunction function = parseAggregate(item.get("aggregate"), path + ".aggregate");
        return new AggregateSelection(function, field, alias);
    }

    /**
     * 解析连接列表。
     *
     * @param value 原始连接列表
     * @param path 字段路径
     * @return 连接项
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Join> parseJoins(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        List<?> values = requireList(value, path);
        if (values.size() > MAX_JOINS) {
            throw failure("JOIN_COUNT_EXCEEDED", path, "join count exceeds the configured limit");
        }
        List<Join> joins = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            joins.add(parseJoin(values.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(joins);
    }

    /**
     * 解析单个连接。
     *
     * @param value 原始连接对象
     * @param path 字段路径
     * @return 类型化连接
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Join parseJoin(Object value, String path) {
        Map<String, Object> item = requireMap(value, path);
        requireKeys(item, Set.of("type", "table", "on"), path);
        JoinType type = parseJoinType(readRequiredString(item, "type", path + ".type"), path + ".type");
        TableSource table = parseTable(item, "table", path + ".table");
        List<JoinCondition> on = parseJoinConditions(item.get("on"), path + ".on");
        return new Join(type, table, on);
    }

    /**
     * 解析连接 ON 条件。
     *
     * @param value 原始 ON 条件
     * @param path 字段路径
     * @return ON 条件列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<JoinCondition> parseJoinConditions(Object value, String path) {
        if (value == null) {
            throw failure("JOIN_ON_REQUIRED", path, "join must declare an ON condition");
        }
        List<?> values = requireList(value, path);
        if (values.isEmpty() || values.size() > MAX_CONDITIONS) {
            throw failure("JOIN_ON_REQUIRED", path, "join must declare bounded ON conditions");
        }
        List<JoinCondition> conditions = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> item = requireMap(values.get(index), path + "[" + index + "]");
            String itemPath = path + "[" + index + "]";
            requireKeys(item, Set.of("left", "right"), itemPath);
            conditions.add(new JoinCondition(
                    parseQualifiedField(readRequiredString(item, "left", itemPath + ".left"), itemPath + ".left"),
                    parseQualifiedField(readRequiredString(item, "right", itemPath + ".right"), itemPath + ".right")));
        }
        return List.copyOf(conditions);
    }

    /**
     * 解析过滤条件。
     *
     * @param value 原始过滤条件
     * @param path 字段路径
     * @return 过滤条件列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Predicate> parseWhere(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            if (values.size() > MAX_CONDITIONS) {
                throw failure("CONDITION_COUNT_EXCEEDED", path, "condition count exceeds the configured limit");
            }
            List<Predicate> predicates = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                predicates.add(parsePredicate(values.get(index), path + "[" + index + "]"));
            }
            return List.copyOf(predicates);
        }
        return List.of(parsePredicate(value, path));
    }

    /**
     * 解析单个过滤条件或逻辑条件。
     *
     * @param value 原始条件对象
     * @param path 字段路径
     * @return 类型化条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Predicate parsePredicate(Object value, String path) {
        Map<String, Object> item = requireMap(value, path);
        boolean comparison = item.containsKey("field");
        boolean logical = item.containsKey("and") || item.containsKey("or");
        if (comparison == logical) {
            throw failure("PREDICATE_SHAPE_INVALID", path, "predicate must be a comparison or logical group");
        }
        if (comparison) {
            return parseComparison(item, path);
        }
        return parseLogical(item, path);
    }

    /**
     * 解析字段比较条件。
     *
     * @param item 条件对象
     * @param path 字段路径
     * @return 比较条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static ComparisonPredicate parseComparison(Map<String, Object> item, String path) {
        requireKeys(item, Set.of("field", "operator", "parameter", "logicalType"), path);
        RelationalField field = parseField(item, "field", path + ".field");
        ComparisonOperator operator = parseComparisonOperator(
                readRequiredString(item, "operator", path + ".operator"), path + ".operator");
        ParameterReference parameter = new ParameterReference(
                readRequiredString(item, "parameter", path + ".parameter"),
                readRequiredString(item, "logicalType", path + ".logicalType"));
        return new ComparisonPredicate(field, operator, parameter);
    }

    /**
     * 解析逻辑条件组。
     *
     * @param item 条件对象
     * @param path 字段路径
     * @return 逻辑条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static LogicalPredicate parseLogical(Map<String, Object> item, String path) {
        boolean and = item.containsKey("and");
        boolean or = item.containsKey("or");
        if (and && or) {
            throw failure("PREDICATE_SHAPE_INVALID", path, "logical group must use one operator");
        }
        String key = and ? "and" : "or";
        requireKeys(item, Set.of(key), path);
        List<?> values = requireList(item.get(key), path + "." + key);
        if (values.isEmpty() || values.size() > MAX_CONDITIONS) {
            throw failure("CONDITION_COUNT_EXCEEDED", path + "." + key, "logical condition count is outside the limit");
        }
        List<Predicate> predicates = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            predicates.add(parsePredicate(values.get(index), path + "." + key + "[" + index + "]"));
        }
        return new LogicalPredicate(and ? LogicalOperator.AND : LogicalOperator.OR, predicates);
    }

    /**
     * 解析分组字段。
     *
     * @param value 原始分组列表
     * @param path 字段路径
     * @return 分组字段
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<RelationalField> parseGroupBy(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        List<?> values = requireList(value, path);
        List<RelationalField> fields = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            fields.add(parseQualifiedField(readString(values.get(index), path + "[" + index + "]"),
                    path + "[" + index + "]"));
        }
        return List.copyOf(fields);
    }

    /**
     * 解析排序项。
     *
     * @param value 原始排序列表
     * @param path 字段路径
     * @return 排序项
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Ordering> parseOrderBy(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        List<?> values = requireList(value, path);
        if (values.size() > MAX_ORDER_ITEMS) {
            throw failure("ORDER_COUNT_EXCEEDED", path, "order item count exceeds the configured limit");
        }
        List<Ordering> orderings = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String itemPath = path + "[" + index + "]";
            Map<String, Object> item = requireMap(values.get(index), itemPath);
            requireKeys(item, Set.of("field", "direction"), itemPath);
            RelationalField field = parseField(item, "field", itemPath + ".field");
            OrderDirection direction = parseOrderDirection(
                    readRequiredString(item, "direction", itemPath + ".direction"), itemPath + ".direction");
            orderings.add(new Ordering(field, direction));
        }
        return List.copyOf(orderings);
    }

    /**
     * 解析来源表字段。
     *
     * @param root 根对象
     * @param key 来源字段名
     * @param path 字段路径
     * @return 来源表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static TableSource parseTable(Map<String, Object> root, String key, String path) {
        if (!root.containsKey(key)) {
            throw failure("MISSING_FIELD", path, "required field is missing");
        }
        Map<String, Object> item = requireMap(root.get(key), path);
        requireKeys(item, Set.of("table", "alias"), path);
        return new TableSource(
                parseIdentifier(readRequiredString(item, "table", path + ".table"), path + ".table"),
                parseIdentifier(readRequiredString(item, "alias", path + ".alias"), path + ".alias"));
    }

    /**
     * 解析投影或排序字段。
     *
     * @param root 字段对象
     * @param key 字段名
     * @param path 字段路径
     * @return 限定字段
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static RelationalField parseField(Map<String, Object> root, String key, String path) {
        return parseQualifiedField(readRequiredString(root, key, path), path);
    }

    /**
     * 解析安全标识符。
     *
     * @param value 标识符文本
     * @param path 字段路径
     * @return 标识符
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String parseIdentifier(String value, String path) {
        if (!isIdentifier(value)) {
            throw failure("INVALID_IDENTIFIER", path, "identifier contains unsupported characters");
        }
        return value;
    }

    /**
     * 解析限定字段文本。
     *
     * @param value 字段文本
     * @param path 字段路径
     * @return 限定字段
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static RelationalField parseQualifiedField(String value, String path) {
        String normalized = value.trim();
        int separator = normalized.indexOf('.');
        if (separator <= 0 || separator != normalized.lastIndexOf('.')
                || !isIdentifier(normalized.substring(0, separator))
                || !isIdentifier(normalized.substring(separator + 1))) {
            throw failure("INVALID_FIELD", path, "field must use an alias-qualified identifier");
        }
        return new RelationalField(normalized.substring(0, separator), normalized.substring(separator + 1));
    }

    /**
     * 解析聚合函数。
     *
     * @param value 函数字符串
     * @param path 字段路径
     * @return 聚合函数
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static AggregateFunction parseAggregate(Object value, String path) {
        return parseEnum(value, path, AggregateFunction.class, "aggregate function");
    }

    /**
     * 解析连接类型。
     *
     * @param value 类型字符串
     * @param path 字段路径
     * @return 连接类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static JoinType parseJoinType(String value, String path) {
        return parseEnum(value, path, JoinType.class, "join type");
    }

    /**
     * 解析比较运算符。
     *
     * @param value 运算符字符串
     * @param path 字段路径
     * @return 比较运算符
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static ComparisonOperator parseComparisonOperator(String value, String path) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "eq" -> ComparisonOperator.EQ;
            case "neq", "not-eq" -> ComparisonOperator.NOT_EQ;
            case "gt" -> ComparisonOperator.GT;
            case "gte" -> ComparisonOperator.GTE;
            case "lt" -> ComparisonOperator.LT;
            case "lte" -> ComparisonOperator.LTE;
            case "like" -> ComparisonOperator.LIKE;
            case "in" -> ComparisonOperator.IN;
            default -> throw failure("INVALID_OPERATOR", path, "unsupported comparison operator");
        };
    }

    /**
     * 解析排序方向。
     *
     * @param value 方向字符串
     * @param path 字段路径
     * @return 排序方向
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static OrderDirection parseOrderDirection(String value, String path) {
        return parseEnum(value, path, OrderDirection.class, "order direction");
    }

    /**
     * 解析枚举字符串。
     *
     * @param value 原始值
     * @param path 字段路径
     * @param type 枚举类型
     * @param description 字段描述
     * @param <T> 枚举类型
     * @return 枚举值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static <T extends Enum<T>> T parseEnum(Object value, String path, Class<T> type, String description) {
        String normalized = readString(value, path).toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_ENUM", path, "unsupported " + description);
        }
    }

    /**
     * 校验结构深度和节点总数。
     *
     * @param value 输入值
     * @param path 字段路径
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void checkShape(Object value, String path) {
        checkShape(value, path, 0, new ShapeBudget());
    }

    /**
     * 递归检查结构预算。
     *
     * @param value 输入值
     * @param path 字段路径
     * @param depth 当前深度
     * @param budget 结构预算
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void checkShape(Object value, String path, int depth, ShapeBudget budget) {
        budget.nodes++;
        if (depth > MAX_DEPTH || budget.nodes > MAX_NODES) {
            throw failure("INPUT_COMPLEXITY_EXCEEDED", path, "DSL structure exceeds the configured bound");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                checkShape(entry.getKey(), path + ".key", depth + 1, budget);
                checkShape(entry.getValue(), path + ".value", depth + 1, budget);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                checkShape(list.get(index), path + "[" + index + "]", depth + 1, budget);
            }
        }
    }

    /**
     * 校验对象字段白名单。
     *
     * @param value 对象
     * @param allowed 允许字段
     * @param path 字段路径
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void requireKeys(Map<String, Object> value, Set<String> allowed, String path) {
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) {
                throw failure("UNKNOWN_FIELD", path + "." + key, "field is not allowed");
            }
        }
    }

    /**
     * 读取必填列表。
     *
     * @param root 对象
     * @param key 字段名
     * @param path 字段路径
     * @return 列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<?> readRequiredList(Map<String, Object> root, String key, String path) {
        if (!root.containsKey(key)) {
            throw failure("MISSING_FIELD", path, "required field is missing");
        }
        return requireList(root.get(key), path);
    }

    /**
     * 读取必填整数。
     *
     * @param root 对象
     * @param key 字段名
     * @param path 字段路径
     * @return 整数值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static int readRequiredInt(Map<String, Object> root, String key, String path) {
        if (!root.containsKey(key) || !(root.get(key) instanceof Number number)) {
            throw failure(root.containsKey(key) ? "INVALID_TYPE" : "MISSING_FIELD", path, "field must be an integer");
        }
        long value = number.longValue();
        if (value != number.doubleValue() || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw failure("INVALID_TYPE", path, "field must be an integer");
        }
        if (value < 1) {
            throw failure("LIMIT_INVALID", path, "limit must be positive");
        }
        return (int) value;
    }

    /**
     * 读取必填字符串。
     *
     * @param root 对象
     * @param key 字段名
     * @param path 字段路径
     * @return 字符串
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String readRequiredString(Map<String, Object> root, String key, String path) {
        if (!root.containsKey(key)) {
            throw failure("MISSING_FIELD", path, "required field is missing");
        }
        return readString(root.get(key), path);
    }

    /**
     * 读取可选字符串。
     *
     * @param root 对象
     * @param key 字段名
     * @param path 字段路径
     * @return 可选字符串
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String readOptionalString(Map<String, Object> root, String key, String path) {
        if (!root.containsKey(key) || root.get(key) == null) {
            return null;
        }
        return readString(root.get(key), path);
    }

    /**
     * 读取字符串值。
     *
     * @param value 原始值
     * @param path 字段路径
     * @return 非空字符串
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String readString(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw failure("INVALID_TYPE", path, "field must be a non-blank string");
        }
        return text.trim();
    }

    /**
     * 读取对象值。
     *
     * @param value 原始值
     * @param path 字段路径
     * @return 字符串键对象
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Map<String, Object> requireMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> source)) {
            throw failure("INVALID_TYPE", path, "field must be an object");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw failure("INVALID_TYPE", path, "object keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    /**
     * 读取列表值。
     *
     * @param value 原始值
     * @param path 字段路径
     * @return 列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<?> requireList(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw failure("INVALID_TYPE", path, "field must be an array");
        }
        return list;
    }

    /**
     * 判断是否为安全标识符。
     *
     * @param value 标识符
     * @return 是否安全
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static boolean isIdentifier(String value) {
        if (value.isEmpty() || !(Character.isLetter(value.charAt(0)) || value.charAt(0) == '_')) {
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
     * 解析结构预算。
     */
    private static final class ShapeBudget {

        private int nodes;
    }
}
