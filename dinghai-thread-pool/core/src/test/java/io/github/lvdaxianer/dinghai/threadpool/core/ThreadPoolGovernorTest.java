package io.github.lvdaxianer.dinghai.threadpool.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** 验证线程池治理器的预算与注册行为。 */
public class ThreadPoolGovernorTest {

    /** 全局许可必须限制跨池同时执行数。 */
    @Test
    public void shouldLimitConcurrentTasksAcrossPools() throws Exception {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            ExecutorService first = governor.register(definition("first-work-h"));
            ExecutorService second = governor.register(definition("second-work-m"));
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger concurrent = new AtomicInteger();
            AtomicInteger maximum = new AtomicInteger();
            first.submit(() -> runTask(concurrent, maximum, started, release));
            started.await(2, TimeUnit.SECONDS);
            second.submit(() -> runTask(concurrent, maximum, new CountDownLatch(0), release));
            Thread.sleep(100);
            release.countDown();
            first.shutdown();
            second.shutdown();
            first.awaitTermination(2, TimeUnit.SECONDS);
            second.awaitTermination(2, TimeUnit.SECONDS);
            assertEquals(1, maximum.get());
        }
    }

    /** 重名池必须失败。 */
    @Test
    public void shouldRejectDuplicatePoolName() {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(2)) {
            governor.register(definition("duplicate-work-h"));
            assertThrows(IllegalArgumentException.class,
                    () -> governor.register(definition("duplicate-work-h")));
        }
    }

    /** 已完成的受控任务必须反映到治理快照，供子控 SQLite 采样。 */
    @Test
    public void shouldExposeCompletedTaskCountersInSnapshot() throws Exception {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            ExecutorService executor = governor.register(definition("metric-work-h"));
            CountDownLatch completed = new CountDownLatch(1);
            executor.execute(completed::countDown);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            ThreadPoolSnapshot snapshot = governor.snapshots().getFirst();
            assertEquals(1, snapshot.submitted());
            assertEquals(1, snapshot.completed());
            assertTrue(snapshot.executionMillis() >= 0);
        }
    }

    /** Future 吞掉任务异常时，治理快照仍必须记录失败次数。 */
    @Test
    public void shouldCountFailedSubmittedTask() throws Exception {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            ExecutorService executor = governor.register(definition("failed-work-h"));
            assertThrows(Exception.class,
                    () -> executor.submit(() -> { throw new IllegalStateException("expected"); }).get());
            ThreadPoolSnapshot snapshot = governor.snapshots().getFirst();
            assertEquals(1, snapshot.failed());
        }
    }

    /**
     * 外部 Spring 执行器任务也必须通过已注册池的全局许可运行。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldGovernDecoratedExternalTask() throws Exception {
        try (ThreadPoolGovernor governor = new ThreadPoolGovernor(1)) {
            governor.register(definition("external-work-h"));
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            Thread first = Thread.ofVirtual().start(governor.decorate("external-work-h", () -> {
                firstStarted.countDown();
                await(release);
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            Thread second = Thread.ofVirtual().start(governor.decorate("external-work-h", secondStarted::countDown));
            assertEquals(1L, secondStarted.getCount());

            release.countDown();
            second.join(2_000);
            first.join(2_000);
            assertEquals(0L, secondStarted.getCount());
        }
    }

    private ThreadPoolDefinition definition(String name) {
        return new ThreadPoolDefinition(name, 1, 1, 1, ThreadPoolDefinition.Priority.HIGH,
                ThreadPoolDefinition.Reliability.FAIL_FAST, 10);
    }

    private void runTask(AtomicInteger concurrent, AtomicInteger maximum, CountDownLatch started,
                         CountDownLatch release) {
        int current = concurrent.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
        started.countDown();
        try {
            release.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            concurrent.decrementAndGet();
        }
    }

    /**
     * 等待并发测试的释放信号。
     *
     * @param latch 协作信号
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
