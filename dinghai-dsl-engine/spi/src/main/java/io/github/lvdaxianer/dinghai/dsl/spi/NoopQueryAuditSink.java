package io.github.lvdaxianer.dinghai.dsl.spi;

/**
 * 默认不产生外部副作用的审计实现。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class NoopQueryAuditSink implements QueryAuditSink {

    /**
     * 返回共享无操作实例。
     */
    public static final NoopQueryAuditSink INSTANCE = new NoopQueryAuditSink();

    private NoopQueryAuditSink() {
    }

    /**
     * 忽略审计事件。
     */
    @Override
    public void record(QueryAuditEvent event) {
        // 审计存储由宿主应用注入，不在公共核心中绑定具体实现。
    }
}
