package io.github.lvdaxianer.dinghai.sharedstate;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 共享状态缓存 Micrometer 指标自动装配。
 *
 * <p>Micrometer 为软依赖：仅当类路径存在且容器提供 {@link MeterRegistry} 时
 * 装配指标桥接器；应用声明自定义桥接器 Bean 时默认实现退位。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@AutoConfiguration(after = SharedStateCacheConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class SharedStateCacheMetricsAutoConfiguration {

    /**
     * 创建共享状态缓存 Micrometer 指标桥接器。
     *
     * @param registry Micrometer 指标注册表
     * @param cache 容错共享状态缓存
     * @return 指标桥接器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(SharedStateCacheMetricsBinder.class)
    public SharedStateCacheMetricsBinder sharedStateCacheMetricsBinder(
            MeterRegistry registry, ResilientSharedStateCache cache) {
        return new SharedStateCacheMetricsBinder(registry, cache);
    }
}
