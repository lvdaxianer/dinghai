package io.github.lvdaxianer.dinghai.dsl.spi;

/**
 * DSL 执行阶段指标扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface QueryTelemetry {

    /**
     * 记录不含敏感值的阶段结果。
     *
     * @param stage 阶段名称
     * @param adapterType 数据适配器类型
     * @param durationMs 阶段耗时
     * @param outcome 稳定结果码
     * @param rowCount 返回行数
     */
    void record(String stage, String adapterType, long durationMs, String outcome, long rowCount);
}
