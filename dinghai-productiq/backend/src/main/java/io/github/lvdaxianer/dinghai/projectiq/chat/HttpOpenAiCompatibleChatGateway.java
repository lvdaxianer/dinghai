package io.github.lvdaxianer.dinghai.projectiq.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * 使用 JDK HttpClient 调用 OpenAI Chat Completions 兼容接口。
 *
 * <p>调用超时由配置控制；连接池、专用执行器和取消传播由上层应用负责。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class HttpOpenAiCompatibleChatGateway implements OpenAiCompatibleChatGateway {
    private final OpenAiHttpConfiguration configuration;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    /** 创建 HTTP 网关适配器。 */
    public HttpOpenAiCompatibleChatGateway(OpenAiHttpConfiguration configuration,
                                           HttpClient client, ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.client = Objects.requireNonNull(client, "client");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** 发送不包含完整问题日志的 Chat Completions 请求。 */
    @Override
    public String complete(OpenAiChatRequest request) {
        try {
            HttpResponse<String> response = client.send(buildRequest(request), HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI 网关调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI 网关调用被中断", exception);
        }
    }

    /** 构造带超时和运行时密钥的 HTTP 请求。 */
    private HttpRequest buildRequest(OpenAiChatRequest request) throws IOException {
        return HttpRequest.newBuilder(configuration.endpoint())
                .timeout(configuration.timeout())
                .header("Authorization", "Bearer " + configuration.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body(request))))
                .build();
    }

    /** 只读取兼容响应中的第一条文本答案。 */
    private String parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI 网关返回 HTTP " + response.statusCode());
        }
        try {
            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0)
                    .path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("OpenAI 网关响应缺少答案");
            }
            return content.asText();
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI 网关响应格式无效", exception);
        }
    }

    /** 组装系统约束和用户问题，正文仅来自有界上下文。 */
    private ObjectNode body(OpenAiChatRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model());
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.prompt().instruction());
        messages.addObject().put("role", "user").put("content", promptText(request.prompt()));
        return body;
    }

    /** 将章节证据拼成可审计的文本，不改变字符上限。 */
    private String promptText(AnswerPrompt prompt) {
        StringBuilder text = new StringBuilder(prompt.question().length() + prompt.evidence().characterCount());
        text.append("问题：").append(prompt.question()).append("\n证据：\n");
        prompt.evidence().sections().forEach(section -> text.append("[")
                .append(section.sectionId()).append("] ")
                .append(section.content()).append("\n"));
        return text.toString();
    }
}
