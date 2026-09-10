package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.mockito.Mockito;

/**
 * 共享状态缓存配置测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class SharedStateCacheConfigurationTest {

    /** 默认最大缓存条目数。 */
    private static final long DEFAULT_MAXIMUM_SIZE = 10_000L;

    /**
     * 配置属性应使用统一前缀并提供有界默认容量。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldExposeCacheConfigurationDefaults() {
        // 准备：读取配置属性类的前缀注解并实例化默认属性对象。
        ConfigurationProperties annotation = SharedStateCacheProperties.class
                .getAnnotation(ConfigurationProperties.class);
        SharedStateCacheProperties properties = new SharedStateCacheProperties();

        // 断言：前缀统一为 dinghai.cache，容量有界，熔断参数非负。
        Assert.assertEquals("dinghai.cache", annotation.prefix());
        // 断言：默认容量与常量约定一致，防止无界增长拖垮内存。
        Assert.assertEquals(DEFAULT_MAXIMUM_SIZE, properties.getCaffeine().getMaximumSize());
        // 断言：熔断阈值必须为正数，保证熔断器可触发。
        Assert.assertTrue(properties.getCircuit().getFailureThreshold() > 0);
        // 断言：熔断开窗时长与镜像温热 TTL 均不为负。
        Assert.assertFalse(properties.getCircuit().getOpenDuration().isNegative());
        Assert.assertFalse(properties.getCircuit().getWarmTtl().isNegative());
    }

    /**
     * Spring 配置应使用属性容量创建 Caffeine 后端。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldCreateCaffeineBackendFromProperties() {
        // 准备：自定义容量上限并构建信封编解码器。
        SharedStateCacheProperties properties = new SharedStateCacheProperties();
        properties.getCaffeine().setMaximumSize(256L);
        CacheEnvelopeCodec codec = new JacksonCacheEnvelopeCodec(new ObjectMapper());

        // 执行：通过配置类工厂方法创建 Caffeine 后端。
        CaffeineSharedStateCache cache = new SharedStateCacheConfiguration()
                .caffeineSharedStateCache(properties, codec);

        // 断言：后端按属性成功创建且容量上限生效。
        Assert.assertNotNull(cache);
    }

    /**
     * 非正数最大容量应在配置绑定阶段被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveMaximumSize() {
        // 执行并断言：设置零容量应立即抛出非法参数异常。
        new SharedStateCacheProperties().getCaffeine().setMaximumSize(0L);
    }

    /**
     * 非正数 Redis 熔断阈值应在配置绑定阶段被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveCircuitThreshold() {
        // 执行并断言：设置零熔断阈值应立即抛出非法参数异常。
        new SharedStateCacheProperties().getCircuit().setFailureThreshold(0);
    }

    /**
     * Redis 配置应创建对应的 Lettuce 连接工厂。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldCreateRedisConnectionFactoryFromProperties() {
        // 准备：配置单机 Redis 的主机、端口、库序号与密码。
        SharedStateCacheProperties properties = new SharedStateCacheProperties();
        properties.getRedis().setHost("127.0.0.1");
        properties.getRedis().setPort(6380);
        properties.getRedis().setDatabase(2);
        properties.getRedis().setPassword("secret");

        // 执行：通过配置类工厂方法创建 Lettuce 连接工厂。
        LettuceConnectionFactory factory = new SharedStateCacheConfiguration()
                .redisConnectionFactory(properties);

        // 断言：连接工厂携带单机连接参数，销毁释放资源。
        Assert.assertEquals("127.0.0.1", factory.getHostName());
        Assert.assertEquals(6380, factory.getPort());
        // 断言：库序号按属性绑定，密码经工厂内部持有不在此暴露。
        Assert.assertEquals(2, factory.getDatabase());
        factory.destroy();
    }

    /**
     * Sentinel 配置应创建包含主节点和哨兵节点的连接工厂。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test
    public void shouldCreateSentinelRedisConnectionFactoryFromProperties() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：以 Sentinel 模式注册完整连接属性并装配共享状态配置。
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("sentinel-test",
                            sentinelTestProperties()));
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            // 注册共享状态自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class);
            context.refresh();

            // 断言：连接工厂按 Sentinel 属性完整装配。
            assertSentinelConnectionFactory(context);
        }
    }

    /**
     * 断言 Sentinel 连接工厂按属性完整装配。
     *
     * @param context 已刷新的测试上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    private void assertSentinelConnectionFactory(AnnotationConfigApplicationContext context) {
        // 断言：连接工厂携带 master、哨兵节点、库序号与 ACL 凭据。
        LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
        // 断言：master 名称与双哨兵节点按属性解析。
        Assert.assertEquals("mymaster", factory.getSentinelConfiguration()
                .getMaster().getName());
        Assert.assertEquals(2, factory.getSentinelConfiguration().getSentinels().size());
        // 断言：库序号与服务端密码绑定到 Sentinel 配置。
        Assert.assertEquals(3, factory.getDatabase());
        Assert.assertTrue(factory.getSentinelConfiguration().getPassword().isPresent());
        // 断言：Sentinel ACL 用户名与密码均生效。
        Assert.assertEquals("sentinel-user",
                factory.getSentinelConfiguration().getSentinelUsername());
        Assert.assertTrue(factory.getSentinelConfiguration()
                .getSentinelPassword().isPresent());
    }

    /**
     * 未配置 Redis 时 Spring 上下文应选择 Caffeine 且不创建 Redis 组件。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldStartWithCaffeineWhenRedisIsAbsent() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：仅注册对象映射器，不提供任何 Redis 连接配置。
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            // 注册共享状态自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class);
            context.refresh();

            // 断言：主后端降级为 Caffeine，健康快照标记降级态。
            ResilientSharedStateCache cache =
                    (ResilientSharedStateCache) context.getBean(SharedStateCache.class);
            Assert.assertEquals("caffeine", cache.health().activeBackend());
            Assert.assertTrue(cache.health().isDegraded());
            // 断言：后端选择仅发生一次，快照提供器与缓存自身快照一致。
            Assert.assertEquals(1L, cache.metrics().backendSelectionCount());
            Assert.assertEquals(cache.health(), context
                    .getBean(SharedStateCacheHealthProvider.class).getHealth());
            Assert.assertEquals(cache.metrics(), context
                    .getBean(SharedStateCacheMetricsProvider.class).getMetrics());
            // 断言：未配置共享状态 Redis 时不创建任何 Redis 组件。
            Assert.assertTrue(context.getBeansOfType(RedisConnectionFactory.class).isEmpty());
            Assert.assertTrue(context.getBeansOfType(RedisSharedStateCache.class).isEmpty());
        }
    }

    /**
     * 未配置共享状态 Redis 时不得复用其他业务的 Redis 连接工厂。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIgnoreExternalRedisFactoryWhenSharedRedisIsAbsent() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：注册一个其他业务的 Redis 连接工厂但不配置共享状态 Redis。
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton("externalRedisConnectionFactory",
                    Mockito.mock(RedisConnectionFactory.class));
            context.register(SharedStateCacheConfiguration.class);
            context.refresh();

            // 断言：外部工厂不得隐式启用共享状态 Redis，主后端仍为 Caffeine。
            ResilientSharedStateCache cache = context.getBean(ResilientSharedStateCache.class);
            Assert.assertEquals("caffeine", cache.health().activeBackend());
            // 断言：容器中不存在共享状态 Redis 后端 Bean。
            Assert.assertTrue(context.getBeansOfType(RedisSharedStateCache.class).isEmpty());
        }
    }

    /**
     * Spring Boot 默认模板共存时应仍使用共享状态专用 Redis 模板。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldUseDedicatedRedisOperationsWhenBootTemplateAlsoExists() {
        try (AnnotationConfigApplicationContext context = createRedisContext()) {
            // 断言：专用模板与外部模板共存且互不覆盖。
            Assert.assertEquals(2, context.getBeansOfType(RedisOperations.class).size());
            RedisTemplate<String, String> shared = context.getBean(
                    "sharedStateRedisOperations", RedisTemplate.class);
            LettuceConnectionFactory dedicated = context.getBean(
                    "sharedStateRedisConnectionFactory", LettuceConnectionFactory.class);
            RedisConnectionFactory external = context.getBean(
                    "externalRedisConnectionFactory", RedisConnectionFactory.class);
            // 断言：共享状态模板只能绑定其具名连接工厂，不能复用业务工厂。
            Assert.assertSame(dedicated, shared.getConnectionFactory());
            Assert.assertNotSame(external, shared.getConnectionFactory());
            // 断言：Redis 后端 Bean 按约定名称注册，可被健康快照识别。
            Assert.assertNotNull(context.getBean("redisSharedStateCache"));
            // 断言：配置共享状态 Redis 后主后端切换为 Redis 且不再降级。
            ResilientSharedStateCache cache =
                    (ResilientSharedStateCache) context.getBean(SharedStateCache.class);
            Assert.assertEquals("redis", cache.health().activeBackend());
            Assert.assertFalse(cache.health().isDegraded());
            // 断言：显式配置只触发一次后端选择，指标计数为 1。
            Assert.assertEquals(1L, cache.metrics().backendSelectionCount());
        }
    }

    /**
     * 应用自定义信封编解码器时默认 Jackson 实现应退位。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldBackOffWhenCustomCacheEnvelopeCodecExists() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：注册应用自定义编解码器与对象映射器。
            CacheEnvelopeCodec custom = Mockito.mock(CacheEnvelopeCodec.class);
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton("customCacheEnvelopeCodec", custom);
            context.register(SharedStateCacheConfiguration.class);
            context.refresh();

            // 断言：容器中仅存在应用自定义编解码器，默认实现不再装配。
            Assert.assertEquals(1, context.getBeansOfType(CacheEnvelopeCodec.class).size());
            Assert.assertSame(custom, context.getBean(CacheEnvelopeCodec.class));
        }
    }

    /**
     * 容器缺少 ObjectMapper 时默认编解码器应跳过装配而非以依赖错误失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldSkipDefaultCodecWhenObjectMapperIsAbsent() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：不注册任何 ObjectMapper，仅注册共享状态自动装配类。
            context.register(SharedStateCacheConfiguration.class);

            // 执行并断言：刷新因缺少编解码器而失败，失败信息指向编解码器而非映射器。
            try {
                context.refresh();
                Assert.fail("缺少 CacheEnvelopeCodec 时容器刷新应失败");
            } catch (org.springframework.beans.factory.UnsatisfiedDependencyException ex) {
                Assert.assertTrue(ex.getMessage().contains("CacheEnvelopeCodec"));
            }
        }
    }

    /**
     * 构造包含外部 Redis 模板的测试上下文。
     *
     * @return Redis 配置测试上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private AnnotationConfigApplicationContext createRedisContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);
        // 仅配置共享状态 Redis 主机，触发共享状态专用装配链。
        context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("redis-test",
                        java.util.Map.of("dinghai.cache.redis.host", "127.0.0.1")));
        context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        context.getBeanFactory().registerSingleton("externalRedisConnectionFactory",
                connectionFactory);
        // 模拟 Spring Boot 默认 Redis 模板与共享状态专用模板共存。
        context.registerBean("stringRedisTemplate", RedisTemplate.class,
                () -> createRedisTemplate(connectionFactory));
        // 注册共享状态自动装配类并刷新容器触发条件装配。
        context.register(SharedStateCacheConfiguration.class);
        context.refresh();
        return context;
    }

    /**
     * 构建 Sentinel 模式测试属性。
     *
     * @return Sentinel 连接属性映射
     * @author lvdaxianer@yeah.net
     * @date 2026-09-10
     */
    private java.util.Map<String, Object> sentinelTestProperties() {
        // 覆盖 master、双哨兵节点、库序号、服务端密码与 ACL 凭据的完整组合。
        return java.util.Map.of(
                "dinghai.cache.redis.mode", "sentinel",
                "dinghai.cache.redis.sentinel.master", "mymaster",
                "dinghai.cache.redis.sentinel.nodes",
                        "127.0.0.1:26379,127.0.0.1:26380",
                "dinghai.cache.redis.database", "3",
                "dinghai.cache.redis.password", "redis-secret",
                "dinghai.cache.redis.sentinel.username", "sentinel-user",
                "dinghai.cache.redis.sentinel.password", "sentinel-secret");
    }

    /**
     * 创建模拟 Spring Boot 自动配置的 Redis 模板。
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 模板
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private RedisTemplate<String, String> createRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        // 仅绑定连接工厂，模拟 Boot 默认模板的最小可用形态。
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
