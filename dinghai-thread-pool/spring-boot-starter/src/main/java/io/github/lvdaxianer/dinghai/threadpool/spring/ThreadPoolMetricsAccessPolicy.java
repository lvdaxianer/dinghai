package io.github.lvdaxianer.dinghai.threadpool.spring;

/**
 * 子控指标端点的宿主授权边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@FunctionalInterface
public interface ThreadPoolMetricsAccessPolicy {

    /**
     * 判断当前部署是否允许读取指标。
     *
     * @return 是否允许
     */
    boolean isAllowed();
}
