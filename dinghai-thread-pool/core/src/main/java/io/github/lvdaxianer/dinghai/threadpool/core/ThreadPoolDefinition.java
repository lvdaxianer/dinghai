package io.github.lvdaxianer.dinghai.threadpool.core;

import java.util.Objects;

/**
 * 受管线程池的不可变配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record ThreadPoolDefinition(String name, int minimumSize, int maximumSize, int weight,
                                   Priority priority, Reliability reliability, int queueCapacity) {

    /**
     * 创建并校验线程池定义。
     *
     * @param name 池名称
     * @param minimumSize 保底线程数
     * @param maximumSize 最大线程数
     * @param weight 弹性配额权重
     * @param priority 过载优先级
     * @param reliability 可靠性策略
     * @param queueCapacity 有界队列容量
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public ThreadPoolDefinition {
        validateName(name);
        validateNumbers(minimumSize, maximumSize, weight, queueCapacity);
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reliability, "reliability");
    }

    /**
     * 校验池名称。
     *
     * @param name 池名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private static void validateName(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,49}")) {
            throw new IllegalArgumentException("thread pool name must be lowercase kebab-case");
        }
    }

    /**
     * 校验容量参数。
     *
     * @param minimumSize 保底线程数
     * @param maximumSize 最大线程数
     * @param weight 权重
     * @param queueCapacity 队列容量
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private static void validateNumbers(int minimumSize, int maximumSize, int weight, int queueCapacity) {
        if (minimumSize < 0 || maximumSize < 1 || minimumSize > maximumSize || weight < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("invalid thread pool capacity");
        }
    }

    /** 过载优先级。 */
    public enum Priority { HIGH, MEDIUM, LOW }

    /** 任务可靠性策略。 */
    public enum Reliability { FAIL_FAST, PERSIST_FOR_RETRY, BEST_EFFORT_DROP }
}
