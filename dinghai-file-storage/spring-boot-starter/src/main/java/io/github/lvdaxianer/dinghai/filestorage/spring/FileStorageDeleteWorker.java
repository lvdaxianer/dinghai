package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.context.SmartLifecycle;

/** 从持久化任务表轮询并执行 provider 删除的独立 Worker。 */
public final class FileStorageDeleteWorker implements SmartLifecycle, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageDeleteWorker.class);
    private static final long MAX_BACKOFF_MILLIS = TimeUnit.HOURS.toMillis(1L);
    private final FileStorage storage;
    private final FileStorageDeleteTaskRepository repository;
    private final FileStorageProperties.Delete properties;
    private final Clock clock;
    private final TaskDecorator taskDecorator;
    private final String workerId = UUID.randomUUID().toString();
    private final AtomicInteger threadSequence = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ThreadPoolExecutor executor;
    private volatile ScheduledExecutorService poller;

    /** 创建尚未启动的删除 Worker。 */
    public FileStorageDeleteWorker(FileStorage storage, FileStorageDeleteTaskRepository repository,
                                   FileStorageProperties properties, Clock clock) {
        this(storage, repository, properties, clock, task -> task);
    }

    /** 创建带 MDC 任务装饰的删除 Worker。 */
    public FileStorageDeleteWorker(FileStorage storage, FileStorageDeleteTaskRepository repository,
                                   FileStorageProperties properties, Clock clock,
                                   TaskDecorator taskDecorator) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.properties = Objects.requireNonNull(properties, "properties").getDelete();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.taskDecorator = Objects.requireNonNull(taskDecorator, "taskDecorator");
        validateProperties(this.properties);
    }

    /** 单次轮询并执行一个任务，供调度线程与测试复用。 */
    boolean processOnce(long now) {
        String leaseToken = UUID.randomUUID().toString();
        FileStorageDeleteTask task = repository.claimNext(now, workerId, leaseToken,
                now + properties.getLeaseDuration().toMillis()).orElse(null);
        if (task == null) {
            return false;
        }
        try {
            // FileStorage.delete 对不存在对象返回 false，删除本身仍按幂等成功处理。
            storage.delete(ObjectKey.of(task.objectKey()));
            repository.markSuccess(task.taskId(), leaseToken, clock.millis());
        } catch (RuntimeException exception) {
            String safeError = safeError(exception);
            if (task.attemptCount() >= properties.getMaxAttempts()) {
                repository.markFailed(task.taskId(), leaseToken, safeError, clock.millis());
                LOGGER.warn("file delete task reached terminal failure, taskId={}, errorType={}",
                        task.taskId(), safeError);
            } else {
                repository.markRetry(task.taskId(), leaseToken,
                        now + backoffMillis(task.attemptCount()), safeError, clock.millis());
            }
        }
        return true;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = new ThreadPoolExecutor(properties.getWorkerCount(), properties.getWorkerCount(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                namedFactory("file-storage-delete-"), new ThreadPoolExecutor.AbortPolicy());
        poller = Executors.newSingleThreadScheduledExecutor(namedFactory("file-storage-delete-poller-"));
        poller.scheduleWithFixedDelay(this::poll, 0L, properties.getPollInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop(Runnable callback) {
        close();
        callback.run();
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /** 关闭轮询器与有界删除执行器。 */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledExecutorService currentPoller = poller;
        if (currentPoller != null) {
            currentPoller.shutdownNow();
        }
        ThreadPoolExecutor currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdown();
            try {
                if (!currentExecutor.awaitTermination(30L, TimeUnit.SECONDS)) {
                    currentExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                currentExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void poll() {
        ThreadPoolExecutor currentExecutor = executor;
        if (!running.get() || currentExecutor == null) {
            return;
        }
        int slots = properties.getWorkerCount() - currentExecutor.getActiveCount()
                - currentExecutor.getQueue().size();
        for (int index = 0; index < Math.max(0, slots); index++) {
            try {
                Map<String, String> context = MDC.getCopyOfContextMap();
                currentExecutor.execute(taskDecorator.decorate(() -> withContext(context)));
            } catch (RejectedExecutionException exception) {
                // 关闭竞争或队列已满时，任务留在表内等待租约到期后接管。
                return;
            }
        }
    }

    private void withContext(Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            try {
                processOnce(clock.millis());
            } catch (RuntimeException exception) {
                LOGGER.warn("file delete worker iteration failed, errorType={}", safeError(exception));
            }
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private long backoffMillis(int attemptCount) {
        long delay = properties.getRetryBackoff().toMillis();
        for (int index = 1; index < attemptCount; index++) {
            if (delay >= MAX_BACKOFF_MILLIS / 2L) {
                return MAX_BACKOFF_MILLIS;
            }
            delay = Math.min(MAX_BACKOFF_MILLIS, delay * 2L);
        }
        return delay;
    }

    private String safeError(Throwable exception) {
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "delete-failed" : type;
    }

    private ThreadFactory namedFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    prefix + workerId.substring(0, 8) + "-" + threadSequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    private void validateProperties(FileStorageProperties.Delete value) {
        if (value.getWorkerCount() <= 0 || value.getQueueCapacity() <= 0
                || value.getPollInterval() == null || value.getPollInterval().isZero()
                || value.getPollInterval().isNegative() || value.getLeaseDuration() == null
                || value.getLeaseDuration().isZero() || value.getLeaseDuration().isNegative()
                || value.getMaxAttempts() <= 0 || value.getRetryBackoff() == null
                || value.getRetryBackoff().isNegative()) {
            throw new IllegalArgumentException("file delete worker resource limits are invalid");
        }
    }
}
