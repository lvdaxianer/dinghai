package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;

/**
 * 不包含敏感参数和结果内容的查询审计事件。
 *
 * @param traceId 链路标识
 * @param dslType DSL 类型
 * @param dslVersion DSL 版本
 * @param planHash 计划摘要
 * @param adapterType 适配器类型
 * @param outcome 执行结果
 * @param durationMs 执行耗时
 * @param rowCount 返回行数
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record QueryAuditEvent(
        String traceId,
        String dslType,
        int dslVersion,
        String planHash,
        String adapterType,
        String outcome,
        long durationMs,
        long rowCount) {

    /**
     * 校验审计事件。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public QueryAuditEvent {
        traceId = requireText(traceId, "traceId");
        dslType = requireText(dslType, "dslType");
        if (dslVersion < 1) {
            throw new IllegalArgumentException("dslVersion must be positive");
        }
        planHash = requireText(planHash, "planHash");
        adapterType = requireText(adapterType, "adapterType");
        outcome = requireText(outcome, "outcome");
        // 负数耗时或行数会制造错误的运行时指标。
        if (durationMs < 0 || rowCount < 0) {
            throw new IllegalArgumentException("durationMs and rowCount must not be negative");
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
