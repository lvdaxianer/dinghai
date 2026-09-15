package io.github.lvdaxianer.dinghai.threadpool.core;

/**
 * 由业务应用实现的过载任务持久化重试端口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface ThreadPoolRetryPersistence {

    /**
     * 持久化被拒绝任务，调用失败必须向调用方抛出异常。
     *
     * @param poolName 受管池名称
     * @param task 被拒绝的业务任务
     * @throws Exception 持久化失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void persist(String poolName, Runnable task) throws Exception;
}
