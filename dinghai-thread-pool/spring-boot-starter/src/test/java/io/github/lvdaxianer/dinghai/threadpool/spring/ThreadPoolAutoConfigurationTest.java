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

    /** 缺失 reliability 时不得使用隐式默认值启动。 */
    @Test
    public void shouldRejectMissingPoolReliability() {
        contextRunner.withPropertyValues(configuredProperties("", "HIGH", null, null)).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("invalid thread pool reliability")));
    }

    /** PERSIST_FOR_RETRY 必须要求应用提供持久化端口。 */
    @Test
    public void shouldRejectRetryPoolWithoutPersistencePort() {
        contextRunner.withPropertyValues(configuredProperties("PERSIST_FOR_RETRY", "HIGH", null, null)).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("ThreadPoolRetryPersistence")));
    }

    /** BEST_EFFORT_DROP 必须声明可归属的业务 owner。 */
    @Test
    public void shouldRejectBestEffortPoolWithoutOwner() {
        contextRunner.withPropertyValues(configuredProperties("BEST_EFFORT_DROP", "HIGH", null, null)).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("requires an owner")));
    }

    /** 无界或非正容量队列不得进入受管执行器。 */
    @Test
    public void shouldRejectNonPositiveQueueCapacity() {
        contextRunner.withPropertyValues(configuredProperties("FAIL_FAST", "HIGH", "0", null)).run(context ->
                assertTrue(context.getStartupFailure().getMessage().contains("invalid thread pool capacity")));
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
        return configuredProperties("FAIL_FAST", "INVALID", null, null);
    }

    /** 返回可按策略、优先级、队列和 owner 调整的完整池配置。 */
    private String[] configuredProperties(String reliability, String priority, String queueCapacity, String owner) {
        return new String[]{
                "dinghai.thread-pool.enabled=true",
                "dinghai.thread-pool.process-thread-capacity=1",
                "dinghai.thread-pool.managed-worker-budget=1",
                "dinghai.thread-pool.pools.test-work.minimum-size=1",
                "dinghai.thread-pool.pools.test-work.maximum-size=1",
                "dinghai.thread-pool.pools.test-work.weight=1",
                "dinghai.thread-pool.pools.test-work.priority=" + priority,
                "dinghai.thread-pool.pools.test-work.reliability=" + reliability,
                "dinghai.thread-pool.pools.test-work.queue-capacity=" + (queueCapacity == null ? "1" : queueCapacity),
                "dinghai.thread-pool.pools.test-work.owner=" + (owner == null ? "" : owner)
        };
    }
}
