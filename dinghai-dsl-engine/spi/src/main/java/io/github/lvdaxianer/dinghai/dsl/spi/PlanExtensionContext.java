package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;

import java.util.Objects;

/**
 * 计划扩展可以读取的服务端上下文。
 *
 * @param dslType DSL 类型
 * @param dslVersion DSL 版本
 * @param queryContext 服务端构造的查询上下文
 */
public record PlanExtensionContext(String dslType, int dslVersion, QueryContext queryContext) {

    /**
     * 校验扩展上下文。
     */
    public PlanExtensionContext {
        dslType = requireText(dslType, "dslType");
        if (dslVersion < 1) {
            throw new IllegalArgumentException("dslVersion must be positive");
        }
        queryContext = Objects.requireNonNull(queryContext, "queryContext");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
