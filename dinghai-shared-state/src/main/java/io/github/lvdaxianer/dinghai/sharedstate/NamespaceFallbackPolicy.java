package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 缓存命名空间的后端故障处理策略。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public enum NamespaceFallbackPolicy {

    /** 可从权威数据重建的普通缓存允许本地回退。 */
    CACHE_FALLBACK,

    /** 明确接受一致性降级的临时状态允许本地回退。 */
    STATE_FALLBACK,

    /** 必须保持分布式一致性的状态禁止本地回退。 */
    DISTRIBUTED_REQUIRED,

    /** 事件通道在本地后端下仅提供进程内投递。 */
    EVENT_CHANNEL
}
