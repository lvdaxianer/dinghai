package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * Redis 共享状态后端熔断状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public enum SharedStateCircuitState {

    /** Redis 请求正常放行。 */
    CLOSED,

    /** Redis 请求被熔断。 */
    OPEN,

    /** 仅放行一个恢复探测请求。 */
    HALF_OPEN
}
