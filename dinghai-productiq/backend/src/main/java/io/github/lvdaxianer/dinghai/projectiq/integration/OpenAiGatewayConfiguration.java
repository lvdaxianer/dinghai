package io.github.lvdaxianer.dinghai.projectiq.integration;

import java.net.URI;
import java.time.Duration;

/**
 * OpenAI 兼容网关的共享安全连接配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record OpenAiGatewayConfiguration(URI baseUri, String apiKey, Duration timeout) {
    /**
     * 校验网关地址、密钥和请求超时。
     */
    public OpenAiGatewayConfiguration {
        if (!isHttp(baseUri) || apiKey == null || apiKey.isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("OpenAI 网关配置无效");
        }
        baseUri = baseUri.toString().endsWith("/") ? baseUri : URI.create(baseUri + "/");
    }

    /**
     * 解析一个相对 API 路径。
     *
     * @param path 不以斜杠开头的相对路径
     * @return 完整网关 URI
     */
    public URI endpoint(String path) {
        return baseUri.resolve(path);
    }

    /**
     * 判断 URI 是否是可安全调用的 HTTP(S) 地址。
     *
     * @param uri 待校验 URI
     * @return 是否是 HTTP(S)
     */
    private static boolean isHttp(URI uri) {
        return uri != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
    }
}
