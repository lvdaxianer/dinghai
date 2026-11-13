package io.github.lvdaxianer.dinghai.dsl.spi;

import java.lang.System.Logger;
import java.util.Objects;

/**
 * 只记录受控摘要的查询日志接收器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class SafeQueryLogger implements QueryAuditSink {

    private final Logger logger;

    /**
     * 使用指定日志器创建安全日志接收器。
     *
     * @param logger JDK 日志器
     */
    public SafeQueryLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * 记录安全审计摘要。
     *
     * @param event 安全审计事件
     */
    @Override
    public void record(QueryAuditEvent event) {
        QueryAuditEvent value = Objects.requireNonNull(event, "event");
        logger.log(Logger.Level.INFO,
                "dsl execution traceId={0} planHash={1} adapter={2} outcome={3} durationMs={4} rowCount={5}",
                value.traceId(), value.planHash(), value.adapterType(), value.outcome(),
                value.durationMs(), value.rowCount());
    }
}
