package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的缓存命名空间策略注册表。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class NamespacePolicyRegistry {

    /** 按名称索引的命名空间策略。 */
    private final Map<String, NamespacePolicy> policies;

    /**
     * 创建策略注册表。
     *
     * @param policies 命名空间策略集合
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public NamespacePolicyRegistry(Collection<NamespacePolicy> policies) {
        this.policies = Collections.unmodifiableMap(indexPolicies(policies));
    }

    /**
     * 由可变参数创建策略注册表。
     *
     * @param policies 命名空间策略
     * @return 策略注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static NamespacePolicyRegistry of(NamespacePolicy... policies) {
        Objects.requireNonNull(policies, "policies");
        return new NamespacePolicyRegistry(Arrays.asList(policies));
    }

    /**
     * 获取已声明的命名空间策略。
     *
     * @param namespace 命名空间名称
     * @return 命名空间策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public NamespacePolicy require(String namespace) {
        NamespacePolicy policy = policies.get(namespace);
        // 未声明命名空间时拒绝调用，确保每类状态都有显式故障策略。
        if (policy == null) {
            throw new IllegalArgumentException("undeclared cache namespace: " + namespace);
        }
        return policy;
    }

    /**
     * 将策略集合转换为名称索引。
     *
     * @param policies 策略集合
     * @return 名称索引
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static Map<String, NamespacePolicy> indexPolicies(Collection<NamespacePolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        Map<String, NamespacePolicy> indexedPolicies = new LinkedHashMap<>(mapCapacity(policies.size()));
        policies.forEach(policy -> putUnique(indexedPolicies, policy));
        return indexedPolicies;
    }

    /**
     * 添加唯一策略。
     *
     * @param indexedPolicies 名称索引
     * @param policy          待添加策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static void putUnique(Map<String, NamespacePolicy> indexedPolicies, NamespacePolicy policy) {
        NamespacePolicy validPolicy = Objects.requireNonNull(policy, "policy");
        // 重复声明会造成策略歧义，因此注册时直接拒绝。
        if (indexedPolicies.putIfAbsent(validPolicy.name(), validPolicy) != null) {
            throw new IllegalArgumentException("duplicate cache namespace: " + validPolicy.name());
        }
    }

    /**
     * 计算哈希表初始容量。
     *
     * @param expectedSize 预期元素数量
     * @return 初始容量
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static int mapCapacity(int expectedSize) {
        return Math.max(1, (int) (expectedSize / 0.75F) + 1);
    }
}
