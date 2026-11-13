package io.github.lvdaxianer.dinghai.dsl.spi;

import java.time.Duration;

/**
 * 通用 DSL 执行边界。
 *
 * @param maxRequestBytes 请求估算最大字节数
 * @param maxConcurrency 最大并发执行数
 * @param timeout 执行超时
 * @param maxPlanNodes 计划节点最大数量
 * @param maxPlanDepth 计划最大深度
 * @param maxJoins 最大 JOIN 数量
 * @param maxRows 最大返回行数
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record RuntimeExecutionLimits(
        int maxRequestBytes,
        int maxConcurrency,
        Duration timeout,
        int maxPlanNodes,
        int maxPlanDepth,
        int maxJoins,
        int maxRows) {

    /**
     * 使用默认最大返回行数创建执行边界，兼容不配置结果行上限的调用方。
     *
     * @param maxRequestBytes 请求估算最大字节数
     * @param maxConcurrency 最大并发执行数
     * @param timeout 执行超时
     * @param maxPlanNodes 计划节点最大数量
     * @param maxPlanDepth 计划最大深度
     * @param maxJoins 最大 JOIN 数量
     */
    public RuntimeExecutionLimits(
            int maxRequestBytes,
            int maxConcurrency,
            Duration timeout,
            int maxPlanNodes,
            int maxPlanDepth,
            int maxJoins) {
        this(maxRequestBytes, maxConcurrency, timeout, maxPlanNodes, maxPlanDepth, maxJoins, 10_000);
    }

    /**
     * 校验执行边界。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RuntimeExecutionLimits {
        if (maxRequestBytes < 1 || maxConcurrency < 1 || maxPlanNodes < 1
                || maxPlanDepth < 1 || maxJoins < 0 || maxRows < 1) {
            throw new IllegalArgumentException("runtime execution limits are invalid");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    /**
     * 返回安全默认边界。
     *
     * @return 默认执行边界
     */
    public static RuntimeExecutionLimits defaults() {
        return new RuntimeExecutionLimits(1_048_576, 32, Duration.ofSeconds(30), 1_000, 16, 8, 10_000);
    }
}
