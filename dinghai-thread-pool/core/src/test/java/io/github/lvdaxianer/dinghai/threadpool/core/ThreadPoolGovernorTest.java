package io.github.lvdaxianer.dinghai.threadpool.core;

import static org.junit.Assert.assertEquals;
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
}
