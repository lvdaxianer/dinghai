package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证索引 worker 以受限批次驱动可恢复的索引执行器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class IndexJobWorkerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IndexJobWorkerConfiguration.class)
            .withBean(IndexJobRunner.class, () -> successfulRunner());

    /**
     * 单次执行返回持久化索引任务的成功和失败汇总。
     *
     * @throws Exception 执行器调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void executesOneBoundedBatch() throws Exception {
        IndexJobRunner runner = mock(IndexJobRunner.class);
        when(runner.executePending(5)).thenReturn(new IndexJobRunner.ExecutionResult(2, 1));
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        try (IndexJobWorker worker = new IndexJobWorker(runner, 5, Duration.ofSeconds(1), executor)) {
            assertThat(worker.executeOnce()).isEqualTo(new IndexJobRunner.ExecutionResult(2, 1));
        }
    }

    /**
     * 当完整运行时提供执行器时，条件配置应启动唯一的后台 worker。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void createsWorkerWhenTheRunnerIsAvailable() {
        contextRunner.withPropertyValues("productiq.index-worker.interval=1h").run(context ->
                assertThat(context).hasSingleBean(IndexJobWorker.class));
    }

    /**
     * 批次大小和调度间隔必须保持在 worker 可安全执行的范围内。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void rejectsUnboundedOrSubMillisecondWorkerConfiguration() {
        IndexJobRunner runner = successfulRunner();
        assertThatThrownBy(() -> new IndexJobWorker(runner, 1_001, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IndexJobWorker(runner, 1, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 创建每次均返回成功汇总的受控 runner 模拟对象。
     *
     * @return 已存根的 runner
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static IndexJobRunner successfulRunner() {
        IndexJobRunner runner = mock(IndexJobRunner.class);
        try {
            when(runner.executePending(20)).thenReturn(new IndexJobRunner.ExecutionResult(0, 0));
        } catch (Exception exception) {
            throw new IllegalStateException("无法配置索引 worker 测试", exception);
        }
        return runner;
    }
}
