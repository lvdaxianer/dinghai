package io.github.lvdaxianer.dinghai.threadpool.core;

/**
 * 通知业务所有者已发生最佳努力丢弃。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface ThreadPoolDropNotifier {

    /**
     * 记录一个可归属的丢弃事件。
     *
     * @param poolName 受管池名称
     * @param owner 业务所有者
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void dropped(String poolName, String owner);
}
