package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.concurrent.Semaphore;

/**
 * 在任务真实执行期维护全局许可和受管池运行指标。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
final class PermitTaskRunner {

    /**
     * 工具类不允许实例化。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private PermitTaskRunner() {
    }

    /**
     * 执行任务，并保证已获得的许可和活动计数在 finally 中成对释放。
     *
     * @param permits 全局 worker 许可
     * @param command 待执行任务
     * @param submittedAt 任务提交纳秒时间
     * @param statistics 单池统计对象
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    static void run(Semaphore permits, Runnable command, long submittedAt, ThreadPoolTaskStatistics statistics) {
        boolean acquired = false;
        long startedAt = 0;
        try {
            permits.acquire();
            acquired = true;
            startedAt = System.nanoTime();
            statistics.waited(startedAt - submittedAt);
            statistics.started();
            command.run();
        } catch (RuntimeException | Error exception) {
            statistics.failed();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for worker permit", exception);
        } finally {
            finish(permits, statistics, acquired, startedAt);
        }
    }

    /**
     * 仅在成功取得许可后释放许可并写入完成统计。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private static void finish(Semaphore permits, ThreadPoolTaskStatistics statistics, boolean acquired,
                               long startedAt) {
        if (acquired) {
            permits.release();
            statistics.finished();
            statistics.completed(System.nanoTime() - startedAt);
        }
    }
}
