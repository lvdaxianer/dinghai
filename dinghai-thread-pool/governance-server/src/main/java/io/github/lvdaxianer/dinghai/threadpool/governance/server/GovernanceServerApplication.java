package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 单实例 Dinghai 线程池总控服务启动入口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@SpringBootApplication
public class GovernanceServerApplication {
    /**
     * 启动总控 HTTP 服务和本地 SQLite 控制面。
     *
     * @param args Spring Boot 启动参数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public static void main(String[] args) {
        SpringApplication.run(GovernanceServerApplication.class, args);
    }
}
