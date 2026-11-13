package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonPredicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Join;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ParameterReference;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.Predicate;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 关系计划的安全类型化重写器。
 */
public final class RelationalPlanRewriter {

    private RelationalPlanRewriter() {
    }

    /**
     * 将类型化约束写入关系计划。
     *
     * @param plan 原始计划
     * @param extensionId 扩展标识
     * @param constraints 类型化约束
     * @return 不可变的新计划
     */
    public static RelationalQueryPlan rewrite(
            RelationalQueryPlan plan,
            String extensionId,
            List<RelationalConstraint> constraints) {
        RelationalQueryPlan source = Objects.requireNonNull(plan, "plan");
        String id = requireText(extensionId, "extensionId");
        List<RelationalConstraint> values = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        if (values.isEmpty()) {
            return source;
        }

        List<Predicate> where = new ArrayList<>(source.query().where());
        List<Join> joins = new ArrayList<>(source.query().joins());
        List<ParameterBinding> bindings = new ArrayList<>(source.parameters());
        Set<String> parameterNames = new HashSet<>(Math.max(16, bindings.size() * 2));
        for (ParameterBinding binding : bindings) {
            parameterNames.add(binding.name());
        }
        for (int index = 0; index < values.size(); index++) {
            RelationalConstraint constraint = values.get(index);
            validateConstraint(source, constraint);
            String parameterName = parameterName(id, index, parameterNames);
            parameterNames.add(parameterName);
            Predicate predicate = new ComparisonPredicate(
                    constraint.field(),
                    constraint.operator(),
                    new ParameterReference(parameterName, constraint.logicalType()));
            bindings.add(new ParameterBinding(parameterName, constraint.logicalType(), constraint.value()));
            if (constraint.placement() == ConstraintPlacement.WHERE) {
                where.add(predicate);
            } else {
                int joinIndex = findJoin(joins, constraint.field().qualifier());
                if (joinIndex < 0) {
                    throw failure(
                            "EXTENSION_JOIN_TARGET_NOT_FOUND",
                            "constraint JOIN_ON field must target a joined alias");
                }
                Join join = joins.get(joinIndex);
                List<Predicate> additionalOn = new ArrayList<>(join.additionalOn());
                additionalOn.add(predicate);
                joins.set(joinIndex, new Join(join.type(), join.table(), join.on(), additionalOn));
            }
        }

        RelationalQueryAst sourceQuery = source.query();
        RelationalQueryAst rewrittenQuery = new RelationalQueryAst(
                sourceQuery.from(),
                sourceQuery.select(),
                joins,
                where,
                sourceQuery.groupBy(),
                sourceQuery.orderBy(),
                sourceQuery.limit());
        Map<String, Object> metadata = new LinkedHashMap<>(source.metadata());
        metadata.put("conditionCount", number(metadata.get("conditionCount"), 0) + values.size());
        List<String> extensionIds = new ArrayList<>();
        Object existing = metadata.get("appliedPlanExtensions");
        if (existing instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value instanceof String text) {
                    extensionIds.add(text);
                }
            }
        }
        extensionIds.add(id);
        metadata.put("appliedPlanExtensions", List.copyOf(extensionIds));
        return new RelationalQueryPlan(
                rewrittenQuery,
                source.tablesByAlias(),
                bindings,
                Math.max(source.predicateDepth(), 1),
                metadata);
    }

    private static void validateConstraint(RelationalQueryPlan plan, RelationalConstraint constraint) {
        RelationalField field = constraint.field();
        RelationalTableDefinition table = plan.tablesByAlias().get(field.qualifier());
        if (table == null || !table.columns().containsKey(field.name())) {
            throw failure("EXTENSION_FIELD_NOT_ALLOWED", "constraint field is not in the resolved schema");
        }
        RelationalColumnDefinition column = table.columns().get(field.name());
        if (!column.logicalType().equalsIgnoreCase(constraint.logicalType())) {
            throw failure("EXTENSION_PARAMETER_TYPE_INVALID", "extension type does not match field type");
        }
        Object value = constraint.value();
        if (constraint.operator() == ComparisonOperator.IN && !(value instanceof Collection<?>)) {
            throw failure("EXTENSION_PARAMETER_TYPE_INVALID", "IN extension value must be a collection");
        }
        if (constraint.operator() != ComparisonOperator.IN && value instanceof Collection<?>) {
            throw failure("EXTENSION_PARAMETER_TYPE_INVALID", "scalar extension value must not be a collection");
        }
        if (!compatible(constraint.logicalType(), value)) {
            throw failure("EXTENSION_PARAMETER_TYPE_INVALID", "extension value does not match logical type");
        }
        if (constraint.placement() == ConstraintPlacement.JOIN_ON
                && findJoin(plan.query().joins(), field.qualifier()) < 0) {
            throw failure(
                    "EXTENSION_JOIN_TARGET_NOT_FOUND",
                    "constraint JOIN_ON field must target a joined alias");
        }
    }

    private static boolean compatible(String logicalType, Object value) {
        String type = logicalType.toLowerCase(Locale.ROOT);
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty()
                    && collection.stream().allMatch(item -> compatibleScalar(type, item));
        }
        return compatibleScalar(type, value);
    }

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

    private static int findJoin(List<Join> joins, String alias) {
        for (int index = 0; index < joins.size(); index++) {
            if (joins.get(index).table().alias().equals(alias)) {
                return index;
            }
        }
        return -1;
    }

    private static String parameterName(String extensionId, int index, Set<String> used) {
        String base = "__dinghai_ext_" + Integer.toHexString(extensionId.hashCode()) + "_" + index;
        String candidate = base;
        int suffix = 1;
        while (used.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static PlanExtensionException failure(String code, String message) {
        return new PlanExtensionException(code, message);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw failure("INVALID_EXTENSION", name + " must not be blank");
        }
        return normalized;
    }
}
