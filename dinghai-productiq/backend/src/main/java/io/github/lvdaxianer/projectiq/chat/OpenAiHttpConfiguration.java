package io.github.lvdaxianer.projectiq.chat;

import java.net.URI;
import java.time.Duration;

/**
 * OpenAI 兼容 HTTP 配置，密钥由运行环境注入。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record OpenAiHttpConfiguration(URI endpoint, String apiKey, String model, Duration timeout) {
    /** 校验生产 HTTP 调用所需配置。 */
    public OpenAiHttpConfiguration {
        if (endpoint == null || apiKey == null || apiKey.isBlank() || model == null || model.isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("OpenAI HTTP 配置无效");
        }
    }
}
