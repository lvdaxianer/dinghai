package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.concurrent.RejectedExecutionException;

/**
 * 表示受管线程池已达到有界容量的稳定过载错误。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolOverloadException extends RejectedExecutionException {

    /**
     * 创建不暴露执行器内部状态的稳定过载错误。
     *
     * @param poolName 过载池名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolOverloadException(String poolName) {
        super("thread pool is overloaded: " + poolName);
    }
}
