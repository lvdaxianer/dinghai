package io.github.lvdaxianer.dinghai.threadpool.spring;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 在受管线程池任务之间复制并清理 MDC。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class MdcTaskDecorator implements TaskDecorator {

    /** 在提交时快照 MDC，并在执行后恢复工作线程原上下文。 */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> runWithContext(runnable, submitted);
    }

    private void runWithContext(Runnable runnable, Map<String, String> submitted) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        install(submitted);
        try {
            runnable.run();
        } finally {
            install(previous);
        }
    }

    private void install(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
