package io.github.lvdaxianer.dinghai.projectiq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 验证知识助手 Spring Boot 启动入口和稳定应用名称配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@SpringBootTest(classes = KnowledgeAssistantApplication.class)
class KnowledgeAssistantApplicationTest {

    /** 当前 Spring 应用的环境配置。 */
    @Autowired
    private Environment environment;

    /**
     * 验证应用上下文可以加载并暴露稳定的应用名称。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldLoadContextWithStableApplicationName() {
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("knowledge-assistant");
    }
}
