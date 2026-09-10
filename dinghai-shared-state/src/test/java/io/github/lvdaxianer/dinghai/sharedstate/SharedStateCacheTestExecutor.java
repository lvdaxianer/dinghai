package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;

/**
 * 共享状态契约测试专用有界线程池工厂。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class SharedStateCacheTestExecutor {

    /** 测试线程空闲存活秒数。 */
    private static final long THREAD_KEEP_ALIVE_SECONDS = 30L;

    /** 测试线程序号。 */
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    /**
     * 禁止实例化测试辅助类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private SharedStateCacheTestExecutor() {
    }

    /**
     * 创建有界共享状态契约测试线程池。
     *
     * @param workerCount 工作线程数
     * @return 契约测试线程池
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    static ExecutorService create(int workerCount) {
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workerCount),
                SharedStateCacheTestExecutor::newContractThread,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建带有业务名称和异常处理器的契约测试线程。
     *
     * @param runnable 线程任务
     * @return 契约测试线程
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static Thread newContractThread(Runnable runnable) {
        Thread thread = new Thread(runnable,
                "shared-state-contract-" + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((failedThread, error) ->
                Assert.fail("线程执行失败: " + failedThread.getName() + ", " + error.getMessage()));
        return thread;
    }
}
