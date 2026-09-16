package io.github.lvdaxianer.projectiq.chat;

/**
 * SSE 会话的取消句柄，用于将客户端断开传播到下游回答任务。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@FunctionalInterface
public interface ChatStreamHandle {
    /**
     * 取消尚未完成的下游回答任务。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void cancel();
}
