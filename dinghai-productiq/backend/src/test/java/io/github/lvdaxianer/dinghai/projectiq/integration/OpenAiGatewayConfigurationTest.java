package io.github.lvdaxianer.dinghai.projectiq.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证 OpenAI 兼容网关地址会保留部署者指定的 API 前缀。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class OpenAiGatewayConfigurationTest {
    /**
     * 基础地址省略末尾斜杠时，仍应在其下解析 API 相对路径。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void retainsApiPrefixWhenBaseUrlHasNoTrailingSlash() {
        OpenAiGatewayConfiguration configuration = new OpenAiGatewayConfiguration(
                URI.create("https://gateway.example/v1"), "test-key", Duration.ofSeconds(1));

        assertThat(configuration.endpoint("embeddings"))
                .isEqualTo(URI.create("https://gateway.example/v1/embeddings"));
    }
}
