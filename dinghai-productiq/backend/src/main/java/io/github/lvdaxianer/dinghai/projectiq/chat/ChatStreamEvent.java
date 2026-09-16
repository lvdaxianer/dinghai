package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.util.List;
import java.util.Map;

/**
 * SSE 对外输出的稳定事件模型，不携带内部证据正文或模型提示词。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record ChatStreamEvent(String name, Map<String, Object> data) {
    /**
     * 固化事件名称和 JSON 数据，避免调用方之后篡改流消息。
     *
     * @param name SSE event 名称
     * @param data SSE JSON 数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ChatStreamEvent {
        if (name == null || name.isBlank() || data == null) {
            throw new IllegalArgumentException("SSE 事件无效");
        }
        data = Map.copyOf(data);
    }

    /**
     * 创建答案片段事件。
     *
     * @param text 已生成的答案文本
     * @return delta SSE 事件
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent("delta", Map.of("text", text == null ? "" : text));
    }

    /**
     * 创建经过简化的来源事件。
     *
     * @param sources 已公开的章节来源
     * @return sources SSE 事件
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static ChatStreamEvent sources(List<Map<String, String>> sources) {
        return new ChatStreamEvent("sources", Map.of("sources", List.copyOf(sources)));
    }

    /**
     * 创建会话成功完成事件。
     *
     * @return done SSE 事件
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static ChatStreamEvent done() {
        return new ChatStreamEvent("done", Map.of("status", "completed"));
    }

    /**
     * 创建稳定的可处理错误事件。
     *
     * @param code 对客户端公开的错误码
     * @return error SSE 事件
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static ChatStreamEvent error(String code) {
        return new ChatStreamEvent("error", Map.of("code", code));
    }
}
