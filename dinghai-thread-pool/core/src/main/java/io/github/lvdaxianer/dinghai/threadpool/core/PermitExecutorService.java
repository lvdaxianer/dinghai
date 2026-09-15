package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
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

    /**
     * 创建包装器。
     *
     * @param delegate 原始执行器
     * @param permits 全局许可
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    PermitExecutorService(ExecutorService delegate, Semaphore permits) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.permits = Objects.requireNonNull(permits, "permits");
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
        delegate.execute(() -> runWithPermit(command));
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
    private void runWithPermit(Runnable command) {
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            command.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for worker permit", exception);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }
}
