package io.github.lvdaxianer.projectiq.chat;

import java.util.Objects;

/**
 * 将回答端口适配到 OpenAI 兼容网关，不持有或记录模型密钥。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class OpenAiCompatibleAnswerer implements Answerer {
    private final String model;
    private final OpenAiCompatibleChatGateway gateway;

    /** 创建 OpenAI 兼容 Answerer。 */
    public OpenAiCompatibleAnswerer(String model, OpenAiCompatibleChatGateway gateway) {
        this.model = Objects.requireNonNull(model, "model");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    /** 仅把受限 AnswerPrompt 转换为网关请求。 */
    @Override
    public AnswerResult answer(AnswerPrompt prompt) {
        String answer = gateway.complete(new OpenAiChatRequest(model, prompt));
        return new AnswerResult(answer, model);
    }
}
