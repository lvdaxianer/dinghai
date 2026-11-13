package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 编译缓存使用的版本化键。
 *
 * @param dslType DSL 类型
 * @param dslVersion DSL 版本
 * @param planHash 规范化计划摘要
 * @param compilerVersion 编译器版本
 * @param schemaVersion Schema 版本
 * @param policyVersion 策略版本
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record CacheKey(
        String dslType,
        int dslVersion,
        String planHash,
        String compilerVersion,
        String schemaVersion,
        String policyVersion) {

    /**
     * 校验缓存键。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public CacheKey {
        dslType = requireText(dslType, "dslType");
        planHash = requireText(planHash, "planHash");
        compilerVersion = requireText(compilerVersion, "compilerVersion");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        policyVersion = requireText(policyVersion, "policyVersion");
        // 版本必须有效，避免缓存键把未知 DSL 当成可复用结果。
        if (dslVersion < 1) {
            throw new IllegalArgumentException("dslVersion must be positive");
        }
    }

    /**
     * 校验文本字段。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本值
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
}
