package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

/**
 * Redis 代次化熔断状态机测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class RedisCircuitBreakerTest {

    /**
     * 旧请求晚成功不得关闭新一代 OPEN 熔断。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIgnoreLateSuccessFromOlderGeneration() {
        // 固定时间源从 1 秒刻度开始。
        AtomicLong now = new AtomicLong(1_000L);
        // 阈值 1 表示一次失败即打开熔断。
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(
                1, Duration.ofSeconds(1L), now::get);
        // 先后取得两张同代请求票据，模拟在飞的旧请求。
        RedisCircuitBreaker.RequestTicket failedRequest = breaker.request();
        RedisCircuitBreaker.RequestTicket lateSuccess = breaker.request();

        // 第一张票据失败，熔断进入 OPEN 新代次。
        breaker.recordFailure(failedRequest);

        // 断言：旧代次请求晚成功不得关闭新 OPEN。
        Assert.assertFalse(breaker.recordSuccess(lateSuccess));
        Assert.assertEquals(SharedStateCircuitState.OPEN, breaker.state());
        // 推进时间越过熔断窗口，下一次请求转为半开探测。
        now.set(2_001L);
        RedisCircuitBreaker.RequestTicket probe = breaker.request();
        Assert.assertEquals(RedisCircuitBreaker.RequestDecision.PROBE, probe.decision());
        // 断言：同代探测成功才能关闭熔断。
        Assert.assertTrue(breaker.recordSuccess(probe));
        Assert.assertEquals(SharedStateCircuitState.CLOSED, breaker.state());
    }

    /**
     * 半开探测失败应打开新代次并重新等待探测窗口。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldReopenAfterFailedHalfOpenProbe() {
        // 固定时间源从 1 秒刻度开始。
        AtomicLong now = new AtomicLong(1_000L);
        // 阈值 1 表示一次失败即打开熔断。
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(
                1, Duration.ofSeconds(1L), now::get);
        // 直接对首张票据记失败，触发首次打开。
        breaker.recordFailure(breaker.request());
        // 推进时间越过熔断窗口，取得半开探测票据。
        now.set(2_001L);
        RedisCircuitBreaker.RequestTicket probe = breaker.request();

        // 执行：半开探测同样失败。
        // 失败结果应报告本次失败打开了熔断。
        RedisCircuitBreaker.FailureResult failure = breaker.recordFailure(probe);

        // 断言：探测失败打开新代次，窗口重新计时。
        Assert.assertTrue(failure.isOpened());
        Assert.assertEquals(SharedStateCircuitState.OPEN, breaker.state());
        // 窗口未到期前请求一律被拒绝。
        Assert.assertEquals(RedisCircuitBreaker.RequestDecision.REJECT,
                breaker.request().decision());
        // 推进到新的探测窗口后允许下一次探测。
        now.set(3_002L);
        Assert.assertEquals(RedisCircuitBreaker.RequestDecision.PROBE,
                breaker.request().decision());
    }
}
