package io.github.lvdaxianer.dinghai.threadpool.spring;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证线程池 starter 的启动配置边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public class ThreadPoolAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ThreadPoolAutoConfiguration.class);

    /** 启用治理时必须显式声明足以覆盖受管预算的进程线程容量。 */
    @Test
    public void shouldRejectMissingProcessThreadCapacity() {
        contextRunner.withPropertyValues(enabledProperties()).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("process-thread-capacity")));
    }

    /** 非法枚举配置必须在启动阶段返回稳定的治理配置错误。 */
    @Test
    public void shouldRejectInvalidPoolPriorityWithStableError() {
        contextRunner.withPropertyValues(invalidPriorityProperties()).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("invalid thread pool priority")));
    }

    /** 返回启用但缺少进程线程容量的最小配置。 */
    private String[] enabledProperties() {
        return new String[]{
                "dinghai.thread-pool.enabled=true",
                "dinghai.thread-pool.managed-worker-budget=1",
                "dinghai.thread-pool.pools.test-work.minimum-size=1",
                "dinghai.thread-pool.pools.test-work.maximum-size=1",
                "dinghai.thread-pool.pools.test-work.weight=1",
                "dinghai.thread-pool.pools.test-work.priority=HIGH",
                "dinghai.thread-pool.pools.test-work.reliability=FAIL_FAST",
                "dinghai.thread-pool.pools.test-work.queue-capacity=1"
        };
    }

    /** 返回优先级非法的进程容量已声明配置。 */
    private String[] invalidPriorityProperties() {
        return new String[]{
                "dinghai.thread-pool.enabled=true",
                "dinghai.thread-pool.process-thread-capacity=1",
                "dinghai.thread-pool.managed-worker-budget=1",
                "dinghai.thread-pool.pools.test-work.minimum-size=1",
                "dinghai.thread-pool.pools.test-work.maximum-size=1",
                "dinghai.thread-pool.pools.test-work.weight=1",
                "dinghai.thread-pool.pools.test-work.priority=INVALID",
                "dinghai.thread-pool.pools.test-work.reliability=FAIL_FAST",
                "dinghai.thread-pool.pools.test-work.queue-capacity=1"
        };
    }
}
