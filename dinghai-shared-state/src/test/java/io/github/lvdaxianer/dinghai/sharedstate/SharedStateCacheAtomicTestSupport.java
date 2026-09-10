package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 共享状态并发契约测试的线程和结果辅助器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class SharedStateCacheAtomicTestSupport implements AutoCloseable {

    /** 结果等待秒数。 */
    private static final long RESULT_TIMEOUT_SECONDS = 5L;

    /** 工作线程数。 */
    private final int workerCount;

    /** 并发测试线程池。 */
    private final ExecutorService executor;

    /**
     * 创建并发测试辅助器。
     *
     * @param workerCount 工作线程数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    SharedStateCacheAtomicTestSupport(int workerCount) {
        this.workerCount = workerCount;
        executor = SharedStateCacheTestExecutor.create(workerCount);
    }

    /**
     * 创建并发起跑门闩。
     *
     * @return 并发门闩
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    Gate newGate() {
        return new Gate(workerCount);
    }

    /**
     * 创建带序号的并发任务。
     *
     * @param factory 任务工厂
     * @param <T> 任务结果类型
     * @return 并发任务列表
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> List<Callable<T>> indexedTasks(IndexedTaskFactory<T> factory) {
        List<Callable<T>> tasks = new ArrayList<>(workerCount);
        for (int index = 0; index < workerCount; index++) {
            tasks.add(factory.create(index));
        }
        return tasks;
    }

    /**
     * 创建内容相同的并发任务。
     *
     * @param task 单个任务
     * @param <T> 任务结果类型
     * @return 并发任务列表
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> List<Callable<T>> repeatedTasks(Callable<T> task) {
        List<Callable<T>> tasks = new ArrayList<>(workerCount);
        for (int index = 0; index < workerCount; index++) {
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 提交任务并统一释放起跑门。
     *
     * @param gate 并发门闩
     * @param tasks 并发任务
     * @param <T> 任务结果类型
     * @return 任务结果列表
     * @throws Exception 线程未能就绪
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> List<Future<T>> submitAndRelease(Gate gate, List<Callable<T>> tasks) throws Exception {
        List<Future<T>> results = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            results.add(executor.submit(task));
        }
        gate.releaseWhenReady();
        return results;
    }

    /**
     * 执行全部任务。
     *
     * @param tasks 并发任务
     * @param <T> 任务结果类型
     * @return 任务结果列表
     * @throws InterruptedException 等待被中断
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> List<Future<T>> invokeAll(List<Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    /**
     * 等待门闩后执行任务。
     *
     * @param gate 并发门闩
     * @param task 实际任务
     * @param <T> 任务结果类型
     * @return 任务结果
     * @throws Exception 等待或任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> T gatedCall(Gate gate, Callable<T> task) throws Exception {
        gate.awaitRelease();
        return task.call();
    }

    /**
     * 统计成功结果数量。
     *
     * @param results 并发结果
     * @return true 的数量
     * @throws Exception 结果读取失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    long countTrue(List<Future<Boolean>> results) throws Exception {
        long count = 0L;
        for (Future<Boolean> result : results) {
            count += result.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS) ? 1L : 0L;
        }
        return count;
    }

    /**
     * 统计存在值的结果数量。
     *
     * @param results 并发结果
     * @return 存在值的数量
     * @throws Exception 结果读取失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    long countPresent(List<Future<Optional<String>>> results) throws Exception {
        long count = 0L;
        for (Future<Optional<String>> result : results) {
            count += result.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS).isPresent() ? 1L : 0L;
        }
        return count;
    }

    /**
     * 等待全部并发结果。
     *
     * @param results 并发结果
     * @param <T> 结果类型
     * @throws Exception 结果读取失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> void awaitResults(List<Future<T>> results) throws Exception {
        for (Future<T> result : results) {
            result.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 关闭并发测试线程池。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    /**
     * 创建带序号的任务。
     *
     * @param <T> 任务结果类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @FunctionalInterface
    interface IndexedTaskFactory<T> {

        /**
         * 创建指定序号的任务。
         *
         * @param index 任务序号
         * @return 并发任务
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        Callable<T> create(int index);
    }

    /**
     * 协调并发任务同时开始的门闩。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    static final class Gate {

        /** 等待任务就绪的门闩。 */
        private final CountDownLatch ready;

        /** 统一释放任务的门闩。 */
        private final CountDownLatch start = new CountDownLatch(1);

        /**
         * 创建并发门闩。
         *
         * @param workerCount 工作线程数
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private Gate(int workerCount) {
            ready = new CountDownLatch(workerCount);
        }

        /**
         * 标记当前任务就绪并等待统一释放。
         *
         * @throws InterruptedException 等待被中断
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private void awaitRelease() throws InterruptedException {
            ready.countDown();
            start.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        /**
         * 等待全部任务就绪后统一释放。
         *
         * @throws InterruptedException 等待被中断
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private void releaseWhenReady() throws InterruptedException {
            // 等待超时说明有任务未就绪，直接失败避免门闩永久悬置。
            if (!ready.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent tasks were not ready");
            }
            start.countDown();
        }
    }
}
