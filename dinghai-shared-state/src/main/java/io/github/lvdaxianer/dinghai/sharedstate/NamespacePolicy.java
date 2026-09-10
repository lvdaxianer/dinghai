package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 缓存命名空间策略。
 *
 * @param name           命名空间名称
 * @param fallbackPolicy 后端故障处理策略
 * @param isTtlRequired  是否强制要求有效 TTL
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record NamespacePolicy(String name,
                              NamespaceFallbackPolicy fallbackPolicy,
                              boolean isTtlRequired) {

    /**
     * 校验命名空间策略。
     *
     * @param name 命名空间名称
     * @param fallbackPolicy 后端故障处理策略
     * @param isTtlRequired 是否强制要求有效 TTL
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public NamespacePolicy {
        name = requireName(name);
        fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    }

    /**
     * 创建普通缓存回退策略。
     *
     * @param name 命名空间名称
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static NamespacePolicy cacheFallback(String name) {
        return new NamespacePolicy(name, NamespaceFallbackPolicy.CACHE_FALLBACK, false);
    }

    /**
     * 创建临时状态回退策略。
     *
     * @param name 命名空间名称
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static NamespacePolicy stateFallback(String name) {
        return new NamespacePolicy(name, NamespaceFallbackPolicy.STATE_FALLBACK, true);
    }

    /**
     * 创建分布式强一致策略。
     *
     * @param name          命名空间名称
     * @param isTtlRequired 是否强制要求有效 TTL
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static NamespacePolicy distributedRequired(String name, boolean isTtlRequired) {
        return new NamespacePolicy(name, NamespaceFallbackPolicy.DISTRIBUTED_REQUIRED, isTtlRequired);
    }

    /**
     * 创建事件通道策略。
     *
     * @param name 命名空间名称
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static NamespacePolicy eventChannel(String name) {
        return new NamespacePolicy(name, NamespaceFallbackPolicy.EVENT_CHANNEL, false);
    }

    /**
     * 校验命名空间名称。
     *
     * @param name 命名空间名称
     * @return 已校验名称
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static String requireName(String name) {
        // 命名空间缺失时立即拒绝，避免生成无法归类的缓存键。
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("namespace name must not be blank");
        }
        return name;
    }
}
