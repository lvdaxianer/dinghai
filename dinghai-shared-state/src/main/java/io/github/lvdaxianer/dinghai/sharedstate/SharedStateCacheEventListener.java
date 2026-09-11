package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.List;

/**
 * 共享状态缓存运行事件订阅钩子。
 *
 * <p>七类事件发出时按序回调，供 Micrometer 等标准可观测性集成订阅递增指标；
 * 快照提供器仍是指标唯一查询入口，订阅方只做递增，不回写。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
interface SharedStateCacheEventListener {

    /** Redis 失败事件。 */
    String REDIS_FAILURE_EVENT = "failure";

    /** 熔断打开事件。 */
    String CIRCUIT_OPEN_EVENT = "circuit-open";

    /** 降级事件。 */
    String FALLBACK_EVENT = "fallback";

    /** 一致性损失事件。 */
    String CONSISTENCY_LOSS_EVENT = "consistency-loss";

    /** Redis 恢复事件。 */
    String RECOVERY_EVENT = "recovery";

    /** 半开探测事件。 */
    String HALF_OPEN_EVENT = "half-open";

    /** 后端选择事件。 */
    String BACKEND_SELECTION_EVENT = "backend-selection";

    /** 全部七类事件名称。 */
    List<String> ALL_EVENTS = List.of(REDIS_FAILURE_EVENT, CIRCUIT_OPEN_EVENT, FALLBACK_EVENT,
            CONSISTENCY_LOSS_EVENT, RECOVERY_EVENT, HALF_OPEN_EVENT, BACKEND_SELECTION_EVENT);

    /**
     * 接收共享状态缓存运行事件。
     *
     * <p>回调发生在对应结构化日志输出之后；实现必须不抛异常、不阻塞，
     * 回调内不捕获异常，由本契约约束实现方。</p>
     *
     * @param event 事件名称，取值见本接口常量
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    void onEvent(String event);
}
