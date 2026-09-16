package io.github.lvdaxianer.projectiq.chat;

/**
 * 经服务端校验后的聊天问题，避免将空白或过长输入交给下游模型。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record ChatQuestion(String text) {
    /** 单次问题允许的最大字符数。 */
    public static final int MAX_LENGTH = 1_000;

    /**
     * 规范化并校验客户端问题。
     *
     * @param text 客户端 JSON 的 question 字段
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ChatQuestion {
        text = text == null ? "" : text.trim();
        // 空白问题无法形成可用的检索意图。
        if (text.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        // 过长文本会突破检索和下游调用的资源边界。
        } else if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("question 超出最大长度");
        }
    }
}
