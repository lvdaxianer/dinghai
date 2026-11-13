package io.github.lvdaxianer.dinghai.dsl.spi;

/**
 * 默认不产生外部副作用的指标实现。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class NoopQueryTelemetry implements QueryTelemetry {

    /**
     * 返回共享无操作实例。
     */
    public static final NoopQueryTelemetry INSTANCE = new NoopQueryTelemetry();

    private NoopQueryTelemetry() {
    }

    /**
     * 忽略指标事件。
     */
    @Override
    public void record(String stage, String adapterType, long durationMs, String outcome, long rowCount) {
        // 默认不依赖 Micrometer，宿主可通过 QueryTelemetry 接入自己的指标系统。
    }
}
