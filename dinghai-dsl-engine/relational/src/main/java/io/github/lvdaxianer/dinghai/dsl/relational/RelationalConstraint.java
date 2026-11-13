package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 计划扩展可以增加的类型化关系约束。
 *
 * @param field 已解析前的逻辑字段引用
 * @param operator 比较运算符
 * @param logicalType 参数逻辑类型
 * @param value 绑定值
 * @param placement 约束放置位置
 */
public record RelationalConstraint(
        RelationalField field,
        ComparisonOperator operator,
        String logicalType,
        Object value,
        ConstraintPlacement placement) {

    /**
     * 校验并冻结约束值。
     */
    public RelationalConstraint {
        field = Objects.requireNonNull(field, "field");
        operator = Objects.requireNonNull(operator, "operator");
        logicalType = requireText(logicalType, "logicalType");
        value = copyValue(value);
        placement = Objects.requireNonNull(placement, "placement");
    }

    /**
     * 创建关系约束。
     *
     * @param field 字段引用
     * @param operator 比较运算符
     * @param logicalType 逻辑类型
     * @param value 绑定值
     * @param placement 放置位置
     * @return 关系约束
     */
    public static RelationalConstraint of(
            RelationalField field,
            ComparisonOperator operator,
            String logicalType,
            Object value,
            ConstraintPlacement placement) {
        return new RelationalConstraint(field, operator, logicalType, value, placement);
    }

    private static Object copyValue(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw new IllegalArgumentException("value collection must not be empty");
            }
            return List.copyOf(collection);
        }
        return value;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
