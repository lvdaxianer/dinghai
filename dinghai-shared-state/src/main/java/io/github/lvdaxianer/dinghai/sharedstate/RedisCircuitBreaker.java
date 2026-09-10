package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 共享状态 Redis 后端的代次化熔断状态机。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class RedisCircuitBreaker {

    /** 连续失败阈值。 */
    private final int failureThreshold;

    /** 熔断打开毫秒数。 */
    private final long openDurationMillis;

    /** 毫秒时间源。 */
    private final LongSupplier clock;

    /** 当前熔断状态。 */
    private SharedStateCircuitState state = SharedStateCircuitState.CLOSED;

    /** 当前熔断代次。 */
    private long generation;

    /** 连续失败次数。 */
    private long failures;

    /** 最近打开时间。 */
    private long openedAt;

    /**
     * 创建 Redis 熔断器。
     *
     * @param failureThreshold 连续失败阈值
     * @param openDuration 熔断打开时间
     * @param clock 毫秒时间源
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    RedisCircuitBreaker(int failureThreshold, Duration openDuration, LongSupplier clock) {
        // 非正数阈值无法形成有效熔断策略。
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = requirePositive(openDuration).toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 获取当前请求票据。
     *
     * @return Redis 请求票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized RequestTicket request() {
        // 关闭状态允许普通 Redis 请求直接执行。
        if (state == SharedStateCircuitState.CLOSED) {
            return ticket(RequestDecision.ALLOW);
        }
        // 半开已有探测或打开窗口尚未结束时拒绝额外 Redis 请求。
        if (state == SharedStateCircuitState.HALF_OPEN || !probeDue()) {
            return ticket(RequestDecision.REJECT);
        }
        state = SharedStateCircuitState.HALF_OPEN;
        return ticket(RequestDecision.PROBE);
    }

    /**
     * 记录 Redis 操作成功。
     *
     * @param ticket 原请求票据
     * @return true 表示需要清理降级数据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized boolean recordSuccess(RequestTicket ticket) {
        RequestTicket validTicket = Objects.requireNonNull(ticket, "ticket");
        // 旧代次请求不能关闭新一代熔断。
        if (validTicket.generation() != generation) {
            return false;
        }
        // 当前代次半开探测成功时关闭熔断器。
        if (validTicket.decision() == RequestDecision.PROBE
                && state == SharedStateCircuitState.HALF_OPEN) {
            return closeAfterRecovery();
        }
        // 关闭状态普通请求成功时清理阈值前短暂失败。
        if (validTicket.decision() == RequestDecision.ALLOW
                && state == SharedStateCircuitState.CLOSED) {
            return clearTransientFailures();
        }
        return false;
    }

    /**
     * 记录 Redis 操作失败。
     *
     * @param ticket 原请求票据
     * @return 故障结果和后续降级票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized FailureResult recordFailure(RequestTicket ticket) {
        RequestTicket validTicket = Objects.requireNonNull(ticket, "ticket");
        // 已跨代的旧请求失败不再改变当前熔断状态。
        if (validTicket.generation() != generation) {
            return new FailureResult(false, ticket(RequestDecision.REJECT));
        }
        failures++;
        boolean shouldOpen = validTicket.decision() == RequestDecision.PROBE
                || failures >= failureThreshold;
        boolean isOpened = shouldOpen && openCircuit();
        return new FailureResult(isOpened, ticket(RequestDecision.REJECT));
    }

    /**
     * 判断降级结果是否仍属于当前故障代次。
     *
     * @param ticket 降级请求票据
     * @return true 表示本地结果仍可保留
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized boolean retainFallback(RequestTicket ticket) {
        return ticket.generation() == generation
                && (state != SharedStateCircuitState.CLOSED || failures > 0L);
    }

    /**
     * 判断请求票据是否属于当前熔断代次。
     *
     * @param ticket Redis 请求票据
     * @return true 表示票据属于当前熔断代次
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized boolean isCurrent(RequestTicket ticket) {
        return Objects.requireNonNull(ticket, "ticket").generation() == generation;
    }

    /**
     * 获取当前熔断状态。
     *
     * @return 当前熔断状态
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized SharedStateCircuitState state() {
        return state;
    }

    /**
     * 获取当前代次的连续失败次数。
     *
     * @return 连续失败次数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    synchronized long failures() {
        return failures;
    }

    /**
     * 关闭半开熔断并清理失败数。
     *
     * @return true 表示完成恢复
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean closeAfterRecovery() {
        state = SharedStateCircuitState.CLOSED;
        failures = 0L;
        return true;
    }

    /**
     * 清理阈值前短暂失败。
     *
     * @return true 表示此前存在故障
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean clearTransientFailures() {
        boolean isRecovered = failures > 0L;
        failures = 0L;
        return isRecovered;
    }

    /**
     * 打开新一代熔断。
     *
     * @return true 表示发生状态转换
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean openCircuit() {
        boolean isOpened = state != SharedStateCircuitState.OPEN;
        state = SharedStateCircuitState.OPEN;
        openedAt = clock.getAsLong();
        generation++;
        return isOpened;
    }

    /**
     * 判断半开探测时间是否到达。
     *
     * @return true 表示允许发起半开探测
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean probeDue() {
        return clock.getAsLong() - openedAt >= openDurationMillis;
    }

    /**
     * 创建当前代次请求票据。
     *
     * @param decision Redis 请求决定
     * @return 当前代次请求票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private RequestTicket ticket(RequestDecision decision) {
        return new RequestTicket(decision, generation);
    }

    /**
     * 校验熔断打开时间为正数毫秒。
     *
     * @param duration 待校验熔断打开时间
     * @return 已校验熔断打开时间
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private Duration requirePositive(Duration duration) {
        Duration validDuration = Objects.requireNonNull(duration, "openDuration");
        // 非正数或不足一毫秒的时长会导致熔断立即进入探测。
        if (validDuration.isZero() || validDuration.isNegative()
                || validDuration.toMillis() <= 0L) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        return validDuration;
    }

    /**
     * Redis 请求放行决定。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    enum RequestDecision { ALLOW, PROBE, REJECT }

    /**
     * Redis 请求代次票据。
     *
     * @param decision Redis 请求决定
     * @param generation 请求进入熔断器时的代次
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    record RequestTicket(RequestDecision decision, long generation) { }

    /**
     * Redis 故障处理结果。
     *
     * @param isOpened true 表示本次失败打开了熔断器
     * @param fallbackTicket 当前故障代次的降级票据
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    record FailureResult(boolean isOpened, RequestTicket fallbackTicket) { }
}
