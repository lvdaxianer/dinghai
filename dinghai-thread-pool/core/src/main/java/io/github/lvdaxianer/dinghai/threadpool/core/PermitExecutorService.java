package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 在任务运行期间持有全局许可的执行器包装器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
final class PermitExecutorService extends AbstractExecutorService {
    private final ExecutorService delegate;
    private final Semaphore permits;
    private final ThreadPoolTaskStatistics statistics;

    /**
     * 创建包装器。
     *
     * @param delegate 原始执行器
     * @param permits 全局许可
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    PermitExecutorService(ExecutorService delegate, Semaphore permits, ThreadPoolTaskStatistics statistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.permits = Objects.requireNonNull(permits, "permits");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    /**
     * 提交一个会在运行期持有许可的任务。
     *
     * @param command 待执行任务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        statistics.submitted();
        long submittedAt = System.nanoTime();
        try {
            delegate.execute(() -> runWithPermit(command, submittedAt));
        } catch (RejectedExecutionException exception) {
            statistics.rejected();
            throw exception;
        }
    }

    /**
     * 提交可返回结果的任务，并将其异常计入失败指标。
     *
     * @param task 待执行任务
     * @param <T> 结果类型
     * @return 可读取结果的 Future
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        FutureTask<T> future = new FutureTask<>(() -> callWithFailureMetric(task));
        execute(future);
        return future;
    }

    /**
     * 提交无返回值任务，并将其异常计入失败指标。
     *
     * @param task 待执行任务
     * @return 可等待完成的 Future
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(task, null);
    }

    /**
     * 提交带固定返回值的任务，并将其异常计入失败指标。
     *
     * @param task 待执行任务
     * @param result 正常完成时返回的值
     * @param <T> 结果类型
     * @return 可读取结果的 Future
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task, "task");
        return submit(() -> {
            task.run();
            return result;
        });
    }

    /**
     * 关闭委托执行器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    /**
     * 立即关闭委托执行器。
     *
     * @return 未执行任务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    /**
     * 判断是否关闭。
     *
     * @return 是否关闭
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    /**
     * 判断是否终止。
     *
     * @return 是否终止
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    /**
     * 等待终止。
     *
     * @param timeout 超时
     * @param unit 时间单位
     * @return 是否终止
     * @throws InterruptedException 被中断
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    /**
     * 在任务执行期获取并释放许可。
     *
     * @param command 待执行任务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void runWithPermit(Runnable command, long submittedAt) {
        boolean acquired = false;
        long startedAt = 0;
        try {
            permits.acquire();
            acquired = true;
            startedAt = System.nanoTime();
            statistics.waited(startedAt - submittedAt);
            command.run();
        } catch (RuntimeException | Error exception) {
            statistics.failed();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for worker permit", exception);
        } finally {
            if (acquired) {
                permits.release();
                statistics.completed(System.nanoTime() - startedAt);
            }
        }
    }

    /** 运行 Callable 并保留失败计数。 */
    private <T> T callWithFailureMetric(Callable<T> task) throws Exception {
        try {
            return task.call();
        } catch (Exception | Error exception) {
            statistics.failed();
            throw exception;
        }
    }

}
