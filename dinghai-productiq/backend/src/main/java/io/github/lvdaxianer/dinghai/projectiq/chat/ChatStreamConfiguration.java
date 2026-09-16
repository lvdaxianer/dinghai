package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配聊天 SSE 入口的最小安全默认值，真实检索编排可替换回答端口实现。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class ChatStreamConfiguration {
    /**
     * 在检索编排尚未配置时明确拒答，禁止绕过 SQLite 当前性验证编造回答。
     *
     * @return 默认回答端口
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
    */
    @Bean
    @ConditionalOnMissingBean(ChatAnswerGateway.class)
    ChatAnswerGateway chatAnswerGateway() {
        return question -> AnswerResponse.refusal(AnswerStatus.NO_KNOWLEDGE);
    }

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
