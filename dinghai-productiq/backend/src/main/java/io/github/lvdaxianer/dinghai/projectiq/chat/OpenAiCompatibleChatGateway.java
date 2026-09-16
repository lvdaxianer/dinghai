package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * OpenAI Chat Completions 兼容 HTTP 网关端口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@FunctionalInterface
public interface OpenAiCompatibleChatGateway {
    /**
     * 调用兼容网关并返回纯文本答案。
     *
     * @param request 已限制证据的请求
     * @return 网关答案正文
     */
    String complete(OpenAiChatRequest request);
}
