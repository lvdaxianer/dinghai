package io.github.lvdaxianer.dinghai.projectiq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 验证远程运行模式不会在缺失网关密钥时静默回退为离线实现。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class RuntimeRemoteConfigurationTest {
    /**
     * 远程模式缺少 API Key 时必须拒绝启动。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void failsFastWhenRemoteModeHasNoGatewayApiKey() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(KnowledgeAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .properties("productiq.runtime.mode=remote",
                        "productiq.gateway.base-url=http://localhost:18080/v1",
                        "productiq.gateway.api-key=",
                        "productiq.gateway.embedding-model=test-embedding",
                        "productiq.gateway.rerank-model=test-rerank",
                        "productiq.gateway.answer-model=test-answer",
                        "productiq.embedding.dimensions=8",
                        "productiq.qdrant.collection=test")
                .run())
                .isInstanceOf(Exception.class)
                .hasMessageContaining("API key");
    }
}
