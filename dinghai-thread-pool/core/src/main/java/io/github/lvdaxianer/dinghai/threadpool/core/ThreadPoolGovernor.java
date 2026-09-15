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
    private final Map<String, ManagedPool> executors = new ConcurrentHashMap<>();

    /**
     * 创建治理器。
     *
     * @param workerBudget 受管业务 worker 预算
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolGovernor(int workerBudget) {
        if (workerBudget < 1) {
            throw new IllegalArgumentException("worker budget must be positive");
        }
        this.workerBudget = workerBudget;
        permits = new Semaphore(workerBudget, true);
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

    /**
     * 创建带全局许可包装的 JDK 执行器。
     *
     * @param definition 线程池定义
     * @return 受控执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private ManagedPool createExecutor(ThreadPoolDefinition definition) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(definition.minimumSize(), definition.maximumSize(),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(definition.queueCapacity()),
                namedThreadFactory(definition.name()), new ThreadPoolExecutor.AbortPolicy());
        return new ManagedPool(definition, executor, new PermitExecutorService(executor, permits));
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
                               ExecutorService exposed) {
        private ThreadPoolSnapshot snapshot() {
            return new ThreadPoolSnapshot(definition.name(), delegate.getActiveCount(), delegate.getPoolSize(),
                    delegate.getQueue().size(), delegate.getMaximumPoolSize(), delegate.getCompletedTaskCount(),
                    definition.priority().name(), definition.reliability().name());
        }
    }
}
