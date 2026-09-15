package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 维护单个受管线程池的单调任务计数与耗时累计值。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
final class ThreadPoolTaskStatistics {
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong waitMillis = new AtomicLong();
    private final AtomicLong executionMillis = new AtomicLong();
    private final AtomicLong active = new AtomicLong();

    /**
     * 记录一次成功提交。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void submitted() { submitted.incrementAndGet(); }

    /**
     * 记录一次执行器拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void rejected() { rejected.incrementAndGet(); }

    /**
     * 记录一次任务完成及执行耗时。
     *
     * @param executionNanos 任务执行纳秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void completed(long executionNanos) {
        completed.incrementAndGet();
        executionMillis.addAndGet(toMillis(executionNanos));
    }

    /**
     * 记录任务代码抛出的未处理异常。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void failed() { failed.incrementAndGet(); }

    /**
     * 记录从提交到获得执行许可的等待耗时。
     *
     * @param waitNanos 等待纳秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void waited(long waitNanos) { waitMillis.addAndGet(toMillis(waitNanos)); }

    /**
     * 增加当前正在持有全局 worker 许可的任务数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void started() { active.incrementAndGet(); }

    /**
     * 减少当前正在持有全局 worker 许可的任务数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void finished() { active.decrementAndGet(); }

    /**
     * 返回当前持有全局 worker 许可的任务数。
     *
     * @return 运行任务数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long activeCount() { return active.get(); }

    /**
     * 返回提交任务数。
     *
     * @return 提交任务数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long submittedCount() { return submitted.get(); }

    /**
     * 返回已完成任务数。
     *
     * @return 已完成任务数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long completedCount() { return completed.get(); }

    /**
     * 返回被拒绝任务数。
     *
     * @return 被拒绝任务数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long rejectedCount() { return rejected.get(); }

    /**
     * 返回失败任务数。
     *
     * @return 失败任务数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long failedCount() { return failed.get(); }

    /**
     * 返回累计等待毫秒数。
     *
     * @return 累计等待毫秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long waitMillis() { return waitMillis.get(); }

    /**
     * 返回累计执行毫秒数。
     *
     * @return 累计执行毫秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    long executionMillis() { return executionMillis.get(); }

    /**
     * 将纳秒转换为可观测的毫秒值。
     *
     * @param nanos 纳秒值
     * @return 非负毫秒值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private long toMillis(long nanos) { return Math.max(0, nanos / 1_000_000); }
}
