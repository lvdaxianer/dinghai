package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 共享状态缓存 Micrometer 指标自动装配测试。
 *
 * <p>以 {@link AnnotationConfigApplicationContext} 模拟容器装配，
 * 验证 MeterRegistry 存在/缺失与用户自定义桥接器时的条件装配行为。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class SharedStateCacheMetricsAutoConfigurationTest {

    /** 临时状态降级命名空间。 */
    private static final String STATE_NAMESPACE = "metrics-auto-state";

    /** 双精度断言误差。 */
    private static final double DOUBLE_DELTA = 0.0;

    /**
     * 容器存在 MeterRegistry 时应装配指标桥接器并注册熔断仪表与事件计数器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRegisterMetricsWhenMeterRegistryExists() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：注册对象映射器与 Micrometer 内存注册表。
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton("meterRegistry", registry);
            // 注册缓存配置与指标自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class,
                    SharedStateCacheMetricsAutoConfiguration.class);
            context.refresh();

            // 断言：指标桥接器装配成功，熔断仪表读数为关闭态 0。
            Assert.assertNotNull(context.getBean(SharedStateCacheMetricsBinder.class));
            Assert.assertEquals(0.0, registry
                    .get(SharedStateCacheMetricsBinder.CIRCUIT_STATE_METRIC).gauge().value(),
                    DOUBLE_DELTA);
            // 断言：无 Redis 的本地模式选择事件按快照播种为 1。
            Assert.assertEquals(1.0, eventCount(registry,
                    SharedStateCacheEventListener.BACKEND_SELECTION_EVENT), DOUBLE_DELTA);
            assertEventIncrementedByCacheOperation(context, registry);
        }
    }

    /**
     * 容器缺少 MeterRegistry 时不应装配指标桥接器且其余缓存 Bean 正常。
     *
     * <p>类路径缺失 Micrometer 的情形由类级 {@code @ConditionalOnClass} 保证，不在此重复验证。</p>
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldSkipMetricsWhenMeterRegistryAbsent() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：仅注册对象映射器，不提供任何 MeterRegistry。
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            // 注册缓存配置与指标自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class,
                    SharedStateCacheMetricsAutoConfiguration.class);
            context.refresh();

            // 断言：指标桥接器未装配，主缓存与健康快照提供器仍正常。
            Assert.assertTrue(
                    context.getBeansOfType(SharedStateCacheMetricsBinder.class).isEmpty());
            Assert.assertNotNull(context.getBean(SharedStateCache.class));
            Assert.assertNotNull(context.getBean(SharedStateCacheHealthProvider.class));
        }
    }

    /**
     * 应用自定义指标桥接器时默认实现应退位。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldBackOffWhenCustomMetricsBinderExists() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：基于独立缓存实例注册应用自定义指标桥接器。
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            SharedStateCacheMetricsBinder custom = new SharedStateCacheMetricsBinder(registry,
                    standaloneCache());
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton("meterRegistry", registry);
            context.getBeanFactory().registerSingleton("customMetricsBinder", custom);
            // 注册缓存配置与指标自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class,
                    SharedStateCacheMetricsAutoConfiguration.class);
            context.refresh();

            // 断言：容器中仅存在应用自定义桥接器，默认实现不再装配。
            Assert.assertEquals(1,
                    context.getBeansOfType(SharedStateCacheMetricsBinder.class).size());
            Assert.assertSame(custom, context.getBean(SharedStateCacheMetricsBinder.class));
        }
    }

    /**
     * 触发本地模式临时状态读取并断言一致性损失事件计数器递增。
     *
     * @param context 已刷新的测试上下文
     * @param registry Micrometer 内存注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertEventIncrementedByCacheOperation(
            AnnotationConfigApplicationContext context, SimpleMeterRegistry registry) {
        // 准备：临时状态命名空间在无 Redis 模式下读取会记录一致性损失。
        CacheKeyBuilder keyBuilder = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.stateFallback(STATE_NAMESPACE)));
        CacheKey<String> stateKey = keyBuilder.build(
                new CacheKeyNamespace(STATE_NAMESPACE, "auth", "session"), "token", String.class);

        // 执行：通过主缓存读取临时状态键，触发本地模式一致性损失事件。
        context.getBean(SharedStateCache.class).get(stateKey);

        // 断言：一致性损失事件计数器随缓存事件订阅递增。
        Assert.assertEquals(1.0, eventCount(registry,
                SharedStateCacheEventListener.CONSISTENCY_LOSS_EVENT), DOUBLE_DELTA);
    }

    /**
     * 读取带事件标签的计数器当前值。
     *
     * @param registry Micrometer 内存注册表
     * @param event 事件名称
     * @return 计数器当前值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private double eventCount(SimpleMeterRegistry registry, String event) {
        return registry.get(SharedStateCacheMetricsBinder.EVENTS_METRIC)
                .tag(SharedStateCacheMetricsBinder.EVENT_TAG, event).counter().count();
    }

    /**
     * 创建脱离容器的本地模式容错缓存。
     *
     * @return 本地模式容错缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResilientSharedStateCache standaloneCache() {
        // 无 Redis 后端的本地模式门面，仅供自定义桥接器绑定。
        return new ResilientSharedStateCache(Optional.empty(),
                ResilientTestCaches.newCaffeineBackend(),
                new ResilientSharedStateCacheOptions(
                        new SharedStateCacheProperties.CircuitProperties(),
                        System::currentTimeMillis));
    }
}
