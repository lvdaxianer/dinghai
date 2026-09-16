package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import io.github.lvdaxianer.dinghai.projectiq.chat.MdcTaskContext;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 用单线程和有界批次安全执行可恢复的向量索引任务。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class IndexJobWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexJobWorker.class);
    private static final int WORKER_THREADS = 1;
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final long INITIAL_DELAY_MILLIS = 0L;
    private static final String THREAD_PREFIX = "productiq-index-worker-";
    private static final String JOB_NAME = "productiqVectorIndex";
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private final IndexJobRunner runner;
    private final int batchSize;
    private final Duration interval;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    /**
     * 创建持有独立单线程执行器的索引 worker。
     *
     * @param runner 索引任务执行器
     * @param batchSize 每轮最大任务数
     * @param interval 相邻轮次的最小间隔
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public IndexJobWorker(IndexJobRunner runner, int batchSize, Duration interval) {
        this(runner, batchSize, interval, executor());
    }

    /**
     * 创建可注入执行器的索引 worker，供受控测试使用。
     *
     * @param runner 索引任务执行器
     * @param batchSize 每轮最大任务数
     * @param interval 相邻轮次的最小间隔
     * @param executor 独立定时执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    IndexJobWorker(IndexJobRunner runner, int batchSize, Duration interval, ScheduledThreadPoolExecutor executor) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.batchSize = requireBatchSize(batchSize);
        this.interval = requireInterval(interval);
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 启动唯一的固定延迟任务，防止上一批未结束时出现重叠执行。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            future = executor.scheduleWithFixedDelay(MdcTaskContext.wrap(this::executeSafely), INITIAL_DELAY_MILLIS,
                    interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 执行一次有 MDC 边界的索引批次，并返回汇总结果。
     *
     * @return 本批成功和失败任务数
     * @throws SQLException SQLite 操作失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public IndexJobRunner.ExecutionResult executeOnce() throws SQLException {
        beginJobContext();
        long startedAt = System.nanoTime();
        try {
            LOGGER.info("[向量索引] job start, name={}, batchSize={}", JOB_NAME, batchSize);
            IndexJobRunner.ExecutionResult result = runner.executePending(batchSize);
            logCompletion(result, startedAt);
            return result;
        } catch (SQLException | RuntimeException exception) {
            logFailure(startedAt, exception);
            throw exception;
        } finally {
            MDC.clear();
        }
    }

    /**
     * 在周期调度中收敛 SQLite 故障，确保后续周期仍可以执行。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void executeSafely() {
        try {
            executeOnce();
        } catch (SQLException | RuntimeException exception) {
            // executeOnce 已输出包含堆栈的整体失败日志。
        }
    }

    /**
     * 输出包含堆栈但不含知识正文的整体批次失败日志。
     *
     * @param startedAt 本批开始纳秒
     * @param exception 任务失败原因
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void logFailure(long startedAt, Exception exception) {
        LOGGER.error("[向量索引] job failed, name={}, elapsedMs={}", JOB_NAME, elapsedMillis(startedAt), exception);
    }

    /**
     * 为没有上游请求的定时任务建立新的可追踪上下文。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void beginJobContext() {
        MDC.clear();
        MDC.put("traceId", "job-" + UUID.randomUUID().toString().replace("-", ""));
        MDC.put("jobName", JOB_NAME);
    }

    /**
     * 输出不含知识正文和模型输入的批次汇总。
     *
     * @param result 本批任务结果
     * @param startedAt 本批开始纳秒
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void logCompletion(IndexJobRunner.ExecutionResult result, long startedAt) {
        if (result.failed() > 0) {
            LOGGER.warn("[向量索引] job done, name={}, elapsedMs={}, success={}, fail={}", JOB_NAME,
                    elapsedMillis(startedAt), result.succeeded(), result.failed());
        } else {
            LOGGER.info("[向量索引] job done, name={}, elapsedMs={}, success={}, fail={}", JOB_NAME,
                    elapsedMillis(startedAt), result.succeeded(), result.failed());
        }
    }

    /**
     * 校验单批任务数为受限正整数。
     *
     * @param value 配置的批量大小
     * @return 已校验批量大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static int requireBatchSize(int value) {
        if (value <= 0 || value > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("索引任务 batchSize 必须在 1 到 " + MAX_BATCH_SIZE + " 之间");
        }
        return value;
    }

    /**
     * 校验固定延迟为正时间。
     *
     * @param value 配置的轮询间隔
     * @return 已校验轮询间隔
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static Duration requireInterval(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() == 0) {
            throw new IllegalArgumentException("索引任务 interval 必须至少为 1 毫秒");
        }
        return value;
    }

    /**
     * 将单调起点转换为日志使用的毫秒耗时。
     *
     * @param startedAt 本批开始纳秒
     * @return 已耗费毫秒
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /**
     * 创建不与 HTTP 请求线程共享的单线程索引执行器。
     *
     * @return 专用索引执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static ScheduledThreadPoolExecutor executor() {
        return new ScheduledThreadPoolExecutor(WORKER_THREADS, runnable -> namedThread(runnable));
    }

    /**
     * 创建带稳定业务前缀的后台线程。
     *
     * @param runnable 待执行任务
     * @return 后台工作线程
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static Thread namedThread(Runnable runnable) {
        int sequence = THREAD_SEQUENCE.incrementAndGet();
        Thread thread = new Thread(runnable, THREAD_PREFIX + sequence);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * 停止周期任务并回收专用执行器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    @PreDestroy
    public void close() {
        if (future != null) {
            future.cancel(true);
        }
        executor.shutdownNow();
    }
}
