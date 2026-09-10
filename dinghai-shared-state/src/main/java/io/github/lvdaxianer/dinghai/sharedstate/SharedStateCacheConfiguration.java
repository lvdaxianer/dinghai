package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 共享状态缓存后端配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@AutoConfiguration
@EnableConfigurationProperties(SharedStateCacheProperties.class)
public class SharedStateCacheConfiguration {

    /** 共享状态专用 Redis 操作模板 Bean 名称。 */
    private static final String SHARED_STATE_REDIS_OPERATIONS_BEAN =
            "sharedStateRedisOperations";

    /**
     * 创建 Jackson 缓存信封编解码器。
     *
     * @param objectMapper 应用统一对象映射器
     * @return 缓存信封编解码器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    public CacheEnvelopeCodec cacheEnvelopeCodec(ObjectMapper objectMapper) {
        return new JacksonCacheEnvelopeCodec(objectMapper);
    }

    /**
     * 根据项目 Redis 配置创建 Lettuce 连接工厂。
     *
     * @param properties 共享状态缓存配置
     * @return Redis 连接工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    @Conditional(RedisConfiguredCondition.class)
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public LettuceConnectionFactory redisConnectionFactory(SharedStateCacheProperties properties) {
        SharedStateCacheProperties.RedisProperties redis = properties.getRedis();
        redis.validate();
        return new LettuceConnectionFactory(redisConfiguration(redis));
    }

    /**
     * 创建统一字符串 Redis 操作模板。
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 操作模板
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    @Conditional(RedisConfiguredCondition.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = SHARED_STATE_REDIS_OPERATIONS_BEAN)
    public RedisTemplate<String, String> sharedStateRedisOperations(
            RedisConnectionFactory connectionFactory, SharedStateCacheProperties properties) {
        properties.getRedis().validate();
        RedisTemplate<String, String> template = new RedisTemplate<>();
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 根据 Redis 模式创建连接配置。
     *
     * @param redis Redis 连接属性
     * @return Redis 连接配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisConfiguration redisConfiguration(
            SharedStateCacheProperties.RedisProperties redis) {
        // Sentinel 模式走哨兵发现链路，其余按单机直连构建。
        if (redis.getMode() == RedisConnectionProperties.RedisMode.SENTINEL) {
            return sentinelConfiguration(redis);
        }
        return standaloneConfiguration(redis);
    }

    /**
     * 创建单机 Redis 配置。
     *
     * @param redis Redis 连接属性
     * @return 单机 Redis 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisStandaloneConfiguration standaloneConfiguration(
            SharedStateCacheProperties.RedisProperties redis) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getPort());
        configuration.setDatabase(redis.getDatabase());
        configuration.setPassword(redisPassword(redis.getPassword()));
        return configuration;
    }

    /**
     * 创建 Sentinel Redis 配置。
     *
     * @param redis Redis 连接属性
     * @return Sentinel Redis 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisSentinelConfiguration sentinelConfiguration(
            SharedStateCacheProperties.RedisProperties redis) {
        SentinelProperties sentinel = redis.getSentinel();
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration()
                .master(sentinel.getMaster());
        for (String node : sentinel.getNodes()) {
            RedisSentinelNode endpoint = RedisSentinelNode.parse(node);
            configuration.sentinel(endpoint.host(), endpoint.port());
        }
        configuration.setDatabase(redis.getDatabase());
        configuration.setPassword(redisPassword(redis.getPassword()));
        applySentinelCredentials(configuration, sentinel);
        return configuration;
    }

    /**
     * 构造 Redis 服务端密码。
     *
     * @param password Redis 密码
     * @return Redis 密码对象
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisPassword redisPassword(String password) {
        return password == null || password.isBlank()
                ? RedisPassword.none() : RedisPassword.of(password);
    }

    /**
     * 设置 Sentinel 服务端认证信息。
     *
     * @param configuration Sentinel 配置
     * @param sentinel Sentinel 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private void applySentinelCredentials(RedisSentinelConfiguration configuration,
            SentinelProperties sentinel) {
        // Sentinel 用户名非空时才启用 ACL 用户名认证。
        if (sentinel.getUsername() != null && !sentinel.getUsername().isBlank()) {
            configuration.setSentinelUsername(sentinel.getUsername());
        }
        // Sentinel 密码非空时才设置 ACL 密码，避免覆盖匿名访问。
        if (sentinel.getPassword() != null && !sentinel.getPassword().isBlank()) {
            configuration.setSentinelPassword(RedisPassword.of(sentinel.getPassword()));
        }
    }

    /**
     * 创建可选的 Redis 后端。
     *
     * @param operations Redis 操作模板
     * @param envelopeCodec 缓存信封编解码器
     * @return Redis 状态后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
    */
    @Bean
    @Conditional(RedisConfiguredCondition.class)
    @ConditionalOnBean(name = SHARED_STATE_REDIS_OPERATIONS_BEAN)
    public RedisSharedStateCache redisSharedStateCache(
            @Qualifier(SHARED_STATE_REDIS_OPERATIONS_BEAN)
            RedisOperations<String, String> operations,
            CacheEnvelopeCodec envelopeCodec) {
        return new RedisSharedStateCache(operations, envelopeCodec);
    }

    /**
     * 创建单进程 Caffeine 状态后端。
     *
     * @param properties 共享状态缓存配置
     * @param envelopeCodec 缓存信封编解码器
     * @return Caffeine 状态后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    public CaffeineSharedStateCache caffeineSharedStateCache(
            SharedStateCacheProperties properties, CacheEnvelopeCodec envelopeCodec) {
        return new CaffeineSharedStateCache(
                properties.getCaffeine().getMaximumSize(), envelopeCodec);
    }

    /**
     * 根据配置选择 Redis 或 Caffeine 主后端。
     *
     * @param properties 共享状态缓存配置
     * @param redis Redis 后端提供器
     * @param caffeine Caffeine 后端
     * @return 当前主后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    @Primary
    public ResilientSharedStateCache sharedStateCache(SharedStateCacheProperties properties,
            ObjectProvider<RedisSharedStateCache> redis,
            CaffeineSharedStateCache caffeine) {
        return new ResilientSharedStateCache(
                configuredRedis(properties, redis), caffeine,
                new ResilientSharedStateCacheOptions(
                        properties.getCircuit(), System::currentTimeMillis));
    }

    /**
     * 仅在共享状态 Redis 显式配置时返回 Redis 后端。
     *
     * @param properties 共享状态缓存配置
     * @param redis Redis 后端提供器
     * @return 已配置 Redis 后端，否则为空
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private Optional<SharedStateCache> configuredRedis(SharedStateCacheProperties properties,
            ObjectProvider<RedisSharedStateCache> redis) {
        // 其他业务 Redis Bean 不得隐式启用共享状态 Redis。
        if (!properties.getRedis().isConfigured()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.getIfAvailable());
    }

    /**
     * 暴露共享状态缓存健康快照提供器。
     *
     * @param cache 容错共享状态缓存
     * @return 健康快照提供器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    public SharedStateCacheHealthProvider sharedStateCacheHealthProvider(
            ResilientSharedStateCache cache) {
        return new SharedStateCacheHealthProvider(cache);
    }

    /**
     * 暴露共享状态缓存指标快照提供器。
     *
     * @param cache 容错共享状态缓存
     * @return 指标快照提供器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Bean
    public SharedStateCacheMetricsProvider sharedStateCacheMetricsProvider(
            ResilientSharedStateCache cache) {
        return new SharedStateCacheMetricsProvider(cache);
    }
}
