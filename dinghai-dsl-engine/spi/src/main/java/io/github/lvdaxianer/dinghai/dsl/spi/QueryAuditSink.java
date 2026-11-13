package io.github.lvdaxianer.dinghai.dsl.spi;

/**
 * 查询审计事件接收扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface QueryAuditSink {

    /**
     * 接收安全审计事件。
     *
     * @param event 审计事件
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    void record(QueryAuditEvent event);
}
