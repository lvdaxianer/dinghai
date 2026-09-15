package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 创建受管线程池并以全局许可限制同时运行的业务任务。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolGovernor implements AutoCloseable {
    private final int workerBudget;
    private final Semaphore permits;
    private final ThreadPoolRetryPersistence retryPersistence;
    private final ThreadPoolDropNotifier dropNotifier;
    private final Map<String, ManagedPool> executors = new ConcurrentHashMap<>();

    /**
     * 创建治理器。
     *
     * @param workerBudget 受管业务 worker 预算
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolGovernor(int workerBudget) {
        this(workerBudget, null, (poolName, owner) -> { });
    }

    /**
     * 创建带业务重试和丢弃通知端口的治理器。
     *
     * @param workerBudget 受管业务 worker 预算
     * @param retryPersistence 可选持久化重试端口
     * @param dropNotifier 丢弃通知端口
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolGovernor(int workerBudget, ThreadPoolRetryPersistence retryPersistence,
                              ThreadPoolDropNotifier dropNotifier) {
        if (workerBudget < 1) {
            throw new IllegalArgumentException("worker budget must be positive");
        }
        this.workerBudget = workerBudget;
        permits = new Semaphore(workerBudget, true);
        this.retryPersistence = retryPersistence;
        this.dropNotifier = Objects.requireNonNull(dropNotifier, "dropNotifier");
    }

    /**
     * 注册一个池并返回只能提交受控任务的执行器。
     *
     * @param definition 线程池定义
     * @return 受控执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ExecutorService register(ThreadPoolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        validateBudget(definition);
        ManagedPool executor = createExecutor(definition);
        ManagedPool prior = executors.putIfAbsent(definition.name(), executor);
        if (prior != null) {
            executor.exposed().shutdownNow();
            throw new IllegalArgumentException("duplicate thread pool name: " + definition.name());
        }
        return executor.exposed();
    }

    /**
     * 取得已注册的受控执行器。
     *
     * @param name 池名称
     * @return 受控执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ExecutorService executor(String name) {
        ManagedPool pool = executors.get(name);
        if (pool == null) {
            throw new IllegalArgumentException("thread pool is not registered: " + name);
        }
        return pool.exposed();
    }

    /**
     * 包装外部执行器提交的任务，使其复用指定池的全局许可与指标统计。
     *
     * @param name 已注册池名称
     * @param command 外部执行器将执行的任务
     * @return 受全局许可限制的任务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Runnable decorate(String name, Runnable command) {
        ManagedPool pool = managedPool(name);
        Objects.requireNonNull(command, "command");
        pool.statistics().submitted();
        long submittedAt = System.nanoTime();
        return () -> PermitTaskRunner.run(permits, command, submittedAt, pool.statistics());
    }

    /**
     * 判断一个池是否已注册，可用于可选的框架适配。
     *
     * @param name 池名称
     * @return 是否已注册
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public boolean isRegistered(String name) { return executors.containsKey(name); }

    /**
     * 返回受管业务 worker 预算。
     *
     * @return worker 预算
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public int workerBudget() {
        return workerBudget;
    }

    /**
     * 关闭全部受管执行器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Override
    public void close() {
        executors.values().forEach(pool -> pool.exposed().shutdown());
    }

    /** 返回全部受管池的即时快照。 */
    public List<ThreadPoolSnapshot> snapshots() {
        return executors.values().stream().map(ManagedPool::snapshot).toList();
    }

    /**
     * 确保单池上限不会超过整体预算。
     *
     * @param definition 线程池定义
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void validateBudget(ThreadPoolDefinition definition) {
        if (definition.maximumSize() > workerBudget) {
            throw new IllegalArgumentException("thread pool maximum exceeds worker budget");
        }
    }

    /** 返回已注册池，不存在时给出稳定配置错误。 */
    private ManagedPool managedPool(String name) {
        ManagedPool pool = executors.get(name);
        if (pool == null) {
            throw new IllegalArgumentException("thread pool is not registered: " + name);
        }
        return pool;
    }

    /**
     * 创建带全局许可包装的 JDK 执行器。
     *
     * @param definition 线程池定义
     * @return 受控执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private ManagedPool createExecutor(ThreadPoolDefinition definition) {
        ThreadPoolTaskStatistics statistics = new ThreadPoolTaskStatistics();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(definition.minimumSize(), definition.maximumSize(),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(definition.queueCapacity()),
                namedThreadFactory(definition.name()), rejectionHandler(definition, statistics));
        return new ManagedPool(definition, executor, statistics,
                new PermitExecutorService(executor, permits, statistics));
    }

    /** 返回定义可靠性策略对应的非阻塞拒绝处理器。 */
    private ThreadPoolExecutor.AbortPolicy rejectionHandler(ThreadPoolDefinition definition,
                                                            ThreadPoolTaskStatistics statistics) {
        return new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
                handleRejectedTask(definition, statistics, task);
            }
        };
    }

    /** 根据策略返回稳定错误、持久化重试或显式通知丢弃。 */
    private void handleRejectedTask(ThreadPoolDefinition definition, ThreadPoolTaskStatistics statistics,
                                    Runnable task) {
        statistics.rejected();
        if (definition.reliability() == ThreadPoolDefinition.Reliability.FAIL_FAST) {
            throw new ThreadPoolOverloadException(definition.name());
        }
        if (definition.reliability() == ThreadPoolDefinition.Reliability.PERSIST_FOR_RETRY) {
            persistForRetry(definition.name(), task);
            return;
        }
        dropNotifier.dropped(definition.name(), definition.owner());
    }

    /** 将重试持久化端口失败升级为调用方可处理的高优先级错误。 */
    private void persistForRetry(String poolName, Runnable task) {
        if (retryPersistence == null) {
            throw new IllegalStateException("thread pool retry persistence is required: " + poolName);
        }
        try {
            retryPersistence.persist(poolName, task);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot persist rejected thread pool task: " + poolName, exception);
        }
    }

    /**
     * 创建业务可识别的线程工厂。
     *
     * @param name 池名称
     * @return 线程工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private ThreadFactory namedThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(name + "-" + thread.threadId());
            return thread;
        };
    }

    private record ManagedPool(ThreadPoolDefinition definition, ThreadPoolExecutor delegate,
                               ThreadPoolTaskStatistics statistics, ExecutorService exposed) {
        private ThreadPoolSnapshot snapshot() {
            return new ThreadPoolSnapshot(definition.name(), Math.toIntExact(statistics.activeCount()), delegate.getPoolSize(),
                    delegate.getQueue().size(), delegate.getMaximumPoolSize(), statistics.submittedCount(),
                    statistics.completedCount(), statistics.rejectedCount(), statistics.failedCount(),
                    statistics.waitMillis(), statistics.executionMillis(), definition.priority().name(),
                    definition.reliability().name());
        }
    }
}
