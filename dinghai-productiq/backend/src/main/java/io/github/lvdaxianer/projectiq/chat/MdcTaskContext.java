package io.github.lvdaxianer.projectiq.chat;

import java.util.Map;
import org.slf4j.MDC;

/**
 * 在线程池任务边界快照、恢复并清理 MDC，防止复用线程串联请求上下文。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class MdcTaskContext {
    private MdcTaskContext() {
    }

    /**
     * 用当前线程的 MDC 包装异步任务。
     *
     * @param task 原始异步任务
     * @return 可安全提交给复用线程池的任务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> runWithContext(task, captured);
    }

    /**
     * 仅在任务执行窗口恢复快照，结束后清空工作线程 MDC。
     *
     * @param task 原始任务
     * @param captured 提交时的 MDC 快照
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static void runWithContext(Runnable task, Map<String, String> captured) {
        // 有请求上下文时恢复它；无上下文时也先清理旧线程残留。
        if (captured == null || captured.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(captured);
        }
        try {
            task.run();
        } finally {
            MDC.clear();
        }
    }
}
