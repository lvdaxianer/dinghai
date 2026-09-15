package io.github.lvdaxianer.dinghai.threadpool.core;

/**
 * 一个受管线程池的即时运行快照。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record ThreadPoolSnapshot(String name, int active, int poolSize, int queueSize,
                                 int actualMaximum, long completed, String priority,
                                 String reliability) {
}
