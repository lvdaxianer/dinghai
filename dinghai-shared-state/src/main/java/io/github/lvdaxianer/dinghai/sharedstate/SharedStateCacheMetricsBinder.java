package io.github.lvdaxianer.dinghai.sharedstate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 共享状态缓存 Micrometer 指标桥接器。
 *
 * <p>注册熔断状态仪表（CLOSED=0/HALF_OPEN=1/OPEN=2，直读健康快照）与七类事件计数器；
 * 创建时按指标快照播种一次，此后仅由事件订阅递增，不回写内部状态。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class SharedStateCacheMetricsBinder {

    /** 熔断状态仪表名。 */
    public static final String CIRCUIT_STATE_METRIC = "dinghai.cache.circuit.state";

    /** 事件计数器指标名。 */
    public static final String EVENTS_METRIC = "dinghai.cache.events";

    /** 事件计数器标签键。 */
    public static final String EVENT_TAG = "event";

    /** 熔断关闭仪表读数。 */
    private static final int CIRCUIT_CLOSED_VALUE = 0;

    /** 熔断半开仪表读数。 */
    private static final int CIRCUIT_HALF_OPEN_VALUE = 1;

    /** 熔断打开仪表读数。 */
    private static final int CIRCUIT_OPEN_VALUE = 2;

    /** 计数器映射初始容量：7 个事件按默认负载因子 0.75 换算（7/0.75+1）。 */
    private static final int COUNTER_MAP_CAPACITY = 10;

    /** 按事件名称索引的计数器。 */
    private final Map<String, Counter> counters;

    /**
     * 创建指标桥接器并注册熔断仪表与事件计数器。
     *
     * @param registry Micrometer 指标注册表
     * @param cache 容错共享状态缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public SharedStateCacheMetricsBinder(MeterRegistry registry, ResilientSharedStateCache cache) {
        MeterRegistry validRegistry = Objects.requireNonNull(registry, "registry");
        ResilientSharedStateCache validCache = Objects.requireNonNull(cache, "cache");
        this.counters = registerEventCounters(validRegistry);
        Gauge.builder(CIRCUIT_STATE_METRIC, validCache,
                target -> circuitStateValue(target.health().circuitState()))
                .description("共享状态缓存熔断状态：0=CLOSED，1=HALF_OPEN，2=OPEN")
                .register(validRegistry);
        seedFromSnapshot(validCache.metrics().totals());
        // 先播种后订阅：桥接器在启动装配期创建、尚无运行流量，
        // 播种与订阅之间理论上的事件丢失窗口可接受。
        validCache.addEventListener(this::incrementEvent);
    }

    /**
     * 注册七类事件计数器。
     *
     * @param registry Micrometer 指标注册表
     * @return 按事件名称索引的计数器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private Map<String, Counter> registerEventCounters(MeterRegistry registry) {
        Map<String, Counter> registered = new HashMap<>(COUNTER_MAP_CAPACITY);
        for (String event : SharedStateCacheEventListener.ALL_EVENTS) {
            registered.put(event, Counter.builder(EVENTS_METRIC)
                    .description("共享状态缓存运行事件计数")
                    .tag(EVENT_TAG, event)
                    .register(registry));
        }
        return registered;
    }

    /**
     * 按指标快照总计播种计数器，覆盖桥接器装配前已发生的事件。
     *
     * @param totals 指标快照总计
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void seedFromSnapshot(SharedStateCacheMetricTotals totals) {
        counters.get(SharedStateCacheEventListener.REDIS_FAILURE_EVENT)
                .increment(totals.redisFailureCount());
        counters.get(SharedStateCacheEventListener.CIRCUIT_OPEN_EVENT)
                .increment(totals.circuitOpenCount());
        counters.get(SharedStateCacheEventListener.FALLBACK_EVENT)
                .increment(totals.fallbackCount());
        counters.get(SharedStateCacheEventListener.CONSISTENCY_LOSS_EVENT)
                .increment(totals.consistencyLossCount());
        counters.get(SharedStateCacheEventListener.RECOVERY_EVENT)
                .increment(totals.recoveryCount());
        counters.get(SharedStateCacheEventListener.HALF_OPEN_EVENT)
                .increment(totals.halfOpenCount());
        counters.get(SharedStateCacheEventListener.BACKEND_SELECTION_EVENT)
                .increment(totals.backendSelectionCount());
    }

    /**
     * 事件订阅回调，递增对应事件计数器。
     *
     * @param event 事件名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void incrementEvent(String event) {
        Counter counter = counters.get(event);
        // 未知事件名不递增，防止未注册事件造成空指针。
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * 把熔断状态映射为仪表读数。
     *
     * @param state 熔断状态
     * @return 仪表读数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private static int circuitStateValue(SharedStateCircuitState state) {
        return switch (state) {
            case CLOSED -> CIRCUIT_CLOSED_VALUE;
            case HALF_OPEN -> CIRCUIT_HALF_OPEN_VALUE;
            case OPEN -> CIRCUIT_OPEN_VALUE;
        };
    }
}
