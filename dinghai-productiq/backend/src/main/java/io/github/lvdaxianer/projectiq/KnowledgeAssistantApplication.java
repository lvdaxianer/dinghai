package io.github.lvdaxianer.projectiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 产品知识本体问答助手的 Spring Boot 启动入口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@SpringBootApplication
public class KnowledgeAssistantApplication {

    /**
     * 启动产品知识问答后端应用。
     *
     * @param args Spring Boot 启动参数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAssistantApplication.class, args);
    }
}
