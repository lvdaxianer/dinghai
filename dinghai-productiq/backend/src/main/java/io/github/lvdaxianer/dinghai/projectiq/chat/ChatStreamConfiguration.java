package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配聊天 SSE 入口；回答网关由运行时检索配置显式提供。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class ChatStreamConfiguration {
    /**
     * 创建使用可配置整体时限的 SSE 编排器。
     *
     * @param gateway 已装配回答端口
     * @param timeout 下游模型调用整体时限
     * @return SSE 编排服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    ChatStreamService chatStreamService(ChatAnswerGateway gateway,
                                        @Value("${productiq.chat-timeout:30s}") Duration timeout) {
        return new ChatStreamService(gateway, timeout);
    }
}
