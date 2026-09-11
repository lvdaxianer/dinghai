package io.github.lvdaxianer.dinghai.sharedstate;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 共享状态缓存 Actuator 健康指示器自动装配。
 *
 * <p>Actuator 为软依赖：仅当类路径存在 {@link HealthIndicator} 时装配；
 * 降级是可工作态，健康状态始终为 UP，明细携带健康快照全部字段；
 * 应用声明同名 Bean 时默认实现退位。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@AutoConfiguration(after = SharedStateCacheConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
public class SharedStateCacheHealthAutoConfiguration {

    /** 健康指示器 Bean 名称（Actuator 端点中显示为 sharedStateCache）。 */
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
     * 创建共享状态缓存健康指示器。
     *
     * @param healthProvider 健康快照提供器
     * @return 健康指示器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean(name = HEALTH_INDICATOR_BEAN)
    @ConditionalOnBean(SharedStateCacheHealthProvider.class)
    @ConditionalOnMissingBean(name = HEALTH_INDICATOR_BEAN)
    public HealthIndicator sharedStateCacheHealthIndicator(
            SharedStateCacheHealthProvider healthProvider) {
        return () -> healthDetails(healthProvider.getHealth());
    }

    /**
     * 把健康快照映射为始终 UP 的 Actuator 健康状态。
     *
     * @param snapshot 健康快照
     * @return Actuator 健康状态
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private Health healthDetails(SharedStateCacheHealth snapshot) {
        // 降级仍可提供服务，状态恒为 UP，降级事实通过明细字段暴露。
        return Health.up()
                .withDetail(REDIS_CONFIGURED_DETAIL, snapshot.isRedisConfigured())
                .withDetail(CIRCUIT_STATE_DETAIL, snapshot.circuitState().name())
                .withDetail(CONSECUTIVE_FAILURES_DETAIL, snapshot.consecutiveFailures())
                .withDetail(ACTIVE_BACKEND_DETAIL, snapshot.activeBackend())
                .withDetail(DEGRADED_DETAIL, snapshot.isDegraded())
                .build();
    }
}
