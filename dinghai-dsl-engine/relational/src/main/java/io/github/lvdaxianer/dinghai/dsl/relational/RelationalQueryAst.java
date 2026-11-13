package io.github.lvdaxianer.dinghai.dsl.relational;

import java.util.List;
import java.util.Objects;

/**
 * relational-query v1 的不可变类型化查询 AST。
 *
 * @param from 查询起始表
 * @param select 投影项
 * @param joins 连接项
 * @param where 过滤条件
 * @param groupBy 分组字段
 * @param orderBy 排序项
 * @param limit 最大返回行数
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RelationalQueryAst(
        TableSource from,
        List<Selection> select,
        List<Join> joins,
        List<Predicate> where,
        List<RelationalField> groupBy,
        List<Ordering> orderBy,
        int limit) {

    /**
     * 校验并冻结关系查询 AST。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalQueryAst {
        from = Objects.requireNonNull(from, "from");
        select = requireNonEmpty(select, "select");
        joins = List.copyOf(Objects.requireNonNull(joins, "joins"));
        where = List.copyOf(Objects.requireNonNull(where, "where"));
        groupBy = List.copyOf(Objects.requireNonNull(groupBy, "groupBy"));
        orderBy = List.copyOf(Objects.requireNonNull(orderBy, "orderBy"));
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    /**
     * 查询来源表。
     *
     * @param table 物理表名候选
     * @param alias 表别名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record TableSource(String table, String alias) {

        /**
         * 校验来源表。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public TableSource {
            table = requireText(table, "table");
            alias = requireText(alias, "alias");
        }
    }

    /**
     * 限定字段引用。
     *
     * @param qualifier 表别名
     * @param name 字段名
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record RelationalField(String qualifier, String name) {

        /**
         * 校验字段引用。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public RelationalField {
            qualifier = requireText(qualifier, "qualifier");
            name = requireText(name, "name");
        }
    }

    /**
     * 类型化投影项。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public sealed interface Selection permits FieldSelection, AggregateSelection {
    }

    /**
     * 普通字段投影。
     *
     * @param field 字段引用
     * @param alias 输出别名，可为空
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record FieldSelection(RelationalField field, String alias) implements Selection {

        /**
         * 校验字段投影。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public FieldSelection {
            field = Objects.requireNonNull(field, "field");
            alias = optionalText(alias, "alias");
        }
    }

    /**
     * 聚合投影。
     *
     * @param function 聚合函数
     * @param field 聚合字段
     * @param alias 输出别名，可为空
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record AggregateSelection(
            AggregateFunction function,
            RelationalField field,
            String alias) implements Selection {

        /**
         * 校验聚合投影。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public AggregateSelection {
            function = Objects.requireNonNull(function, "function");
            field = Objects.requireNonNull(field, "field");
            alias = optionalText(alias, "alias");
        }
    }

    /**
     * 支持的聚合函数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public enum AggregateFunction {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX
    }

    /**
     * 类型化表连接。
     *
     * @param type 连接类型
     * @param table 被连接表
     * @param on 字段等值 ON 条件
     * @param additionalOn 扩展增加的类型化 ON 谓词
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record Join(
            JoinType type,
            TableSource table,
            List<JoinCondition> on,
            List<Predicate> additionalOn) {

        /**
         * 创建没有扩展 ON 谓词的普通连接。
         *
         * @param type 连接类型
         * @param table 被连接表
         * @param on 字段等值 ON 条件
         */
        public Join(JoinType type, TableSource table, List<JoinCondition> on) {
            this(type, table, on, List.of());
        }

        /**
         * 校验连接条件。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public Join {
            type = Objects.requireNonNull(type, "type");
            table = Objects.requireNonNull(table, "table");
            on = requireNonEmpty(on, "on");
            additionalOn = List.copyOf(Objects.requireNonNull(additionalOn, "additionalOn"));
        }
    }

    /**
     * 连接类型。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public enum JoinType {
        INNER,
        LEFT
    }

    /**
     * 单个字段等值连接条件。
     *
     * @param left 左侧字段
     * @param right 右侧字段
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record JoinCondition(RelationalField left, RelationalField right) {

        /**
         * 校验连接字段。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public JoinCondition {
            left = Objects.requireNonNull(left, "left");
            right = Objects.requireNonNull(right, "right");
        }
    }

    /**
     * 类型化过滤条件。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public sealed interface Predicate permits ComparisonPredicate, LogicalPredicate {
    }

    /**
     * 字段与参数的比较条件。
     *
     * @param field 字段引用
     * @param operator 比较运算符
     * @param parameter 参数引用
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record ComparisonPredicate(
            RelationalField field,
            ComparisonOperator operator,
            ParameterReference parameter) implements Predicate {

        /**
         * 校验比较条件。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public ComparisonPredicate {
            field = Objects.requireNonNull(field, "field");
            operator = Objects.requireNonNull(operator, "operator");
            parameter = Objects.requireNonNull(parameter, "parameter");
        }
    }

    /**
     * 逻辑组合条件。
     *
     * @param operator 逻辑运算符
     * @param predicates 子条件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record LogicalPredicate(LogicalOperator operator, List<Predicate> predicates) implements Predicate {

        /**
         * 校验逻辑条件。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public LogicalPredicate {
            operator = Objects.requireNonNull(operator, "operator");
            predicates = requireNonEmpty(predicates, "predicates");
        }
    }

    /**
     * 比较运算符。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public enum ComparisonOperator {
        EQ,
        NOT_EQ,
        GT,
        GTE,
        LT,
        LTE,
        LIKE,
        IN
    }

    /**
     * 逻辑运算符。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public enum LogicalOperator {
        AND,
        OR
    }

    /**
     * 参数引用定义。
     *
     * @param name 参数名
     * @param logicalType 逻辑类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record ParameterReference(String name, String logicalType) {

        /**
         * 校验参数引用。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public ParameterReference {
            name = requireText(name, "name");
            logicalType = requireText(logicalType, "logicalType");
        }
    }

    /**
     * 类型化排序项。
     *
     * @param field 排序字段
     * @param direction 排序方向
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public record Ordering(RelationalField field, OrderDirection direction) {

        /**
         * 校验排序项。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-11-13
         */
        public Ordering {
            field = Objects.requireNonNull(field, "field");
            direction = Objects.requireNonNull(direction, "direction");
        }
    }

    /**
     * 排序方向。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public enum OrderDirection {
        ASC,
        DESC
    }

    /**
     * 复制非空列表。
     *
     * @param values 原始列表
     * @param name 字段名
     * @param <T> 元素类型
     * @return 不可变非空列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static <T> List<T> requireNonEmpty(List<T> values, String name) {
        List<T> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return copied;
    }

    /**
     * 校验必填文本。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 规范化文本
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

    /**
     * 校验可选文本。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 规范化文本或空值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String optionalText(String value, String name) {
        if (value == null) {
            return null;
        }
        return requireText(value, name);
    }
}
