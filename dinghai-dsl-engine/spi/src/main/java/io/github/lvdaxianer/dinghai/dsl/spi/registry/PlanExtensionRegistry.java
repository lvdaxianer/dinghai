package io.github.lvdaxianer.dinghai.dsl.spi.registry;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtension;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionContext;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 启动时构建并冻结的计划扩展注册表。
 */
public final class PlanExtensionRegistry {

    private final List<PlanExtension> extensions;

    /**
     * 校验、排序并冻结扩展。
     *
     * @param candidates 应用启动时发现的扩展
     */
    public PlanExtensionRegistry(Collection<? extends PlanExtension> candidates) {
        List<PlanExtension> sorted = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (PlanExtension candidate : Objects.requireNonNull(candidates, "candidates")) {
            PlanExtension extension = Objects.requireNonNull(candidate, "plan extension");
            String id = canonicalId(extension.id());
            if (!ids.add(id)) {
                throw new PlanExtensionException(
                        "DUPLICATE_EXTENSION", "Duplicate plan extension registration: " + id);
            }
            sorted.add(extension);
        }
        sorted.sort(Comparator.comparingInt(PlanExtension::order).thenComparing(PlanExtension::id));
        this.extensions = List.copyOf(sorted);
    }

    /**
     * 依次应用适用的扩展。
     *
     * @param plan 原始计划
     * @param context 可信扩展上下文
     * @return 最终计划
     */
    public ExecutionPlan apply(ExecutionPlan plan, PlanExtensionContext context) {
        ExecutionPlan current = Objects.requireNonNull(plan, "plan");
        PlanExtensionContext extensionContext = Objects.requireNonNull(context, "context");
        for (PlanExtension extension : extensions) {
            try {
                if (!extension.supports(current, extensionContext)) {
                    continue;
                }
                current = Objects.requireNonNull(
                        extension.apply(current, extensionContext),
                        "plan extension result");
            } catch (PlanExtensionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new PlanExtensionException(
                        "EXTENSION_FAILED", "plan extension failed", exception);
            }
        }
        return current;
    }

    /**
     * 返回稳定的注册顺序标识。
     *
     * @return 不可变扩展标识列表
     */
    public List<String> registeredIds() {
        return extensions.stream().map(PlanExtension::id).toList();
    }

    /**
     * 返回扩展数量。
     *
     * @return 扩展数量
     */
    public int size() {
        return extensions.size();
    }

    private static String canonicalId(String value) {
        String normalized = Objects.requireNonNull(value, "planExtension.id").trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw new PlanExtensionException(
                    "INVALID_EXTENSION", "plan extension id must be a non-blank canonical value");
        }
        return normalized;
    }
}
