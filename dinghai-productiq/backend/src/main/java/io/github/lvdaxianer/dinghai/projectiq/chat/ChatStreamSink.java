package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * 将稳定聊天事件写入具体传输层的端口，SSE 适配器可在客户端断开时抛出运行时异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public interface ChatStreamSink {
    /**
     * 写出一个按顺序到达的 SSE 事件。
     *
     * @param event 待发送事件
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void emit(ChatStreamEvent event);

    /**
     * 正常结束 SSE 响应。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void complete();
}
