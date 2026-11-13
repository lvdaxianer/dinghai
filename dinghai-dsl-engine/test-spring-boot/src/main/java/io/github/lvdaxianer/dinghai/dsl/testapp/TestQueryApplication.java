package io.github.lvdaxianer.dinghai.dsl.testapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * DSL 引擎真实协议测试应用入口。
 */
@SpringBootApplication
@EnableConfigurationProperties(TestQueryProperties.class)
public class TestQueryApplication {

    /**
     * 启动测试应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TestQueryApplication.class, args);
    }
}
