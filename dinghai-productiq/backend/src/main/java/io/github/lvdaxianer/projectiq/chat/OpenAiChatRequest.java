package io.github.lvdaxianer.projectiq.chat;

/**
 * OpenAI 兼容网关请求，正文来自已经限制过的证据上下文。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record OpenAiChatRequest(String model, AnswerPrompt prompt) {
    /** 校验网关请求的模型和提示。 */
    public OpenAiChatRequest {
        if (model == null || model.isBlank() || prompt == null) {
            throw new IllegalArgumentException("OpenAI 请求参数无效");
        }
    }
}
