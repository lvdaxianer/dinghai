package io.github.lvdaxianer.dinghai.sharedstate;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 根据 Redis 连接模式判断是否启用共享状态 Redis 连接。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class RedisConfiguredCondition extends SpringBootCondition {

    /** Redis 主机配置键。 */
    private static final String REDIS_HOST_PROPERTY = "dinghai.cache.redis.host";

    /** Redis 模式配置键。 */
    private static final String REDIS_MODE_PROPERTY = "dinghai.cache.redis.mode";

    /** 默认 Redis 模式。 */
    private static final String DEFAULT_REDIS_MODE = "standalone";

    /** Sentinel Redis 模式。 */
    private static final String SENTINEL_REDIS_MODE = "sentinel";

    /**
     * 判断 Redis 连接模式是否完成配置。
     *
     * @param context 条件上下文
     * @param metadata 注解元数据
     * @return 条件匹配结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public ConditionOutcome getMatchOutcome(
            ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty(
                REDIS_MODE_PROPERTY, DEFAULT_REDIS_MODE);
        ConditionMessage.Builder message = ConditionMessage.forCondition("SharedStateRedis");
        // Sentinel 模式由属性对象负责校验 master 和节点，显式选择模式即启用校验链。
        if (SENTINEL_REDIS_MODE.equalsIgnoreCase(mode)) {
            return ConditionOutcome.match(message.foundExactly(REDIS_MODE_PROPERTY));
        }
        // 既非 Sentinel 也非单机的模式属于非法配置，装配前直接拒绝。
        if (!DEFAULT_REDIS_MODE.equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "dinghai.cache.redis.mode must be standalone or sentinel");
        }
        return matchStandalone(context, message);
    }

    /**
     * 按单机模式的 host 配置判断条件匹配结果。
     *
     * @param context 条件上下文
     * @param message 条件消息构建器
     * @return 单机模式条件匹配结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    private ConditionOutcome matchStandalone(
            ConditionContext context, ConditionMessage.Builder message) {
        // 未配置模式时保持旧的单机配置行为，只在 host 非空时装配 Redis。
        if (StringUtils.hasText(context.getEnvironment().getProperty(REDIS_HOST_PROPERTY))) {
            return ConditionOutcome.match(message.foundExactly(REDIS_HOST_PROPERTY));
        }
        // host 未配置时共享状态 Redis 不启用，回退本地后端。
        return ConditionOutcome.noMatch(message.didNotFind("property")
                .items(REDIS_HOST_PROPERTY));
    }
}
