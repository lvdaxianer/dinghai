package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 共享状态缓存 Actuator 健康指示器自动装配测试。
 *
 * <p>以 {@link AnnotationConfigApplicationContext} 模拟容器装配，
 * 验证健康指示器的 UP 语义、快照明细字段与用户自定义同名 Bean 退位。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class SharedStateCacheHealthAutoConfigurationTest {

    /** 健康指示器 Bean 名称。 */
    private static final String HEALTH_INDICATOR_BEAN = "sharedStateCacheHealthIndicator";

    /** 是否配置 Redis 明细键。 */
    private static final String REDIS_CONFIGURED_DETAIL = "isRedisConfigured";

    /** 熔断状态明细键。 */
    private static final String CIRCUIT_STATE_DETAIL = "circuitState";

    /** 连续失败次数明细键。 */
    private static final String CONSECUTIVE_FAILURES_DETAIL = "consecutiveFailures";

    /** 当前后端明细键。 */
    private static final String ACTIVE_BACKEND_DETAIL = "activeBackend";

    /** 是否降级明细键。 */
    private static final String DEGRADED_DETAIL = "isDegraded";

    /**
     * Actuator 存在时应注册始终 UP 且携带全部健康快照字段的健康指示器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRegisterUpHealthIndicatorWithSnapshotDetails() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：仅注册对象映射器，无 Redis 配置使缓存处于本地降级态。
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            // 注册缓存配置与健康指示器自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class,
                    SharedStateCacheHealthAutoConfiguration.class);
            context.refresh();

            // 执行：获取健康指示器并计算健康状态。
            HealthIndicator indicator =
                    context.getBean(HEALTH_INDICATOR_BEAN, HealthIndicator.class);
            Health health = indicator.health();

            // 断言：降级是可工作态，状态始终为 UP。
            Assert.assertEquals(Status.UP, health.getStatus());
            assertSnapshotDetails(health);
        }
    }

    /**
     * 应用自定义同名健康指示器时默认实现应退位。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldBackOffWhenCustomHealthIndicatorExists() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            // 准备：注册应用自定义的同名健康指示器。
            HealthIndicator custom = () -> Health.down().withDetail("custom", true).build();
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton(HEALTH_INDICATOR_BEAN, custom);
            // 注册缓存配置与健康指示器自动装配类并刷新容器触发条件装配。
            context.register(SharedStateCacheConfiguration.class,
                    SharedStateCacheHealthAutoConfiguration.class);
            context.refresh();

            // 断言：同名 Bean 仍为应用自定义实现，默认健康指示器不再装配。
            Assert.assertSame(custom,
                    context.getBean(HEALTH_INDICATOR_BEAN, HealthIndicator.class));
        }
    }

    /**
     * 断言健康明细携带本地降级态快照的全部字段。
     *
     * @param health Actuator 健康状态
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void assertSnapshotDetails(Health health) {
        // 断言：明细标记未配置 Redis。
        Assert.assertEquals(Boolean.FALSE,
                health.getDetails().get(REDIS_CONFIGURED_DETAIL));
        // 断言：熔断处于关闭态。
        Assert.assertEquals(SharedStateCircuitState.CLOSED.name(),
                health.getDetails().get(CIRCUIT_STATE_DETAIL));
        // 断言：无连续失败记录。
        Assert.assertEquals(0L, health.getDetails().get(CONSECUTIVE_FAILURES_DETAIL));
        // 断言：当前后端为 Caffeine。
        Assert.assertEquals("caffeine", health.getDetails().get(ACTIVE_BACKEND_DETAIL));
        // 断言：处于一致性降级状态。
        Assert.assertEquals(Boolean.TRUE, health.getDetails().get(DEGRADED_DETAIL));
    }
}
