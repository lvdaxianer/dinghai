package io.github.lvdaxianer.dinghai.sharedstate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 构建 {@code dinghai:{module}:{resource}:{identifier}} 格式的缓存键。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class CacheKeyBuilder {

    /** 缓存键固定前缀。 */
    private static final String KEY_PREFIX = "dinghai";

    /** 单个键片段的最大字符数。 */
    static final int MAX_SEGMENT_LENGTH = 128;

    /** 拼接后完整缓存键的最大字符数。 */
    static final int MAX_KEY_LENGTH = 512;

    /** 敏感标识符哈希算法。 */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** 命名空间策略注册表。 */
    private final NamespacePolicyRegistry policyRegistry;

    /**
     * 创建缓存键构建器。
     *
     * @param policyRegistry 命名空间策略注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CacheKeyBuilder(NamespacePolicyRegistry policyRegistry) {
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
    }

    /**
     * 使用普通标识符构建缓存键。
     *
     * @param keyNamespace 缓存键路径
     * @param identifier 资源标识符
     * @param valueType 缓存值类型
     * @param <T> 缓存值类型
     * @return 类型化缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public <T> CacheKey<T> build(CacheKeyNamespace keyNamespace, String identifier, Class<T> valueType) {
        CacheKeyNamespace validNamespace = Objects.requireNonNull(keyNamespace, "keyNamespace");
        NamespacePolicy policy = policyRegistry.require(validNamespace.namespace());
        String key = String.join(":", KEY_PREFIX, validNamespace.module(),
                validNamespace.resource(), resolveIdentifier(policy, identifier));
        // 拼接后的完整键同样受长度上限约束，防御超长键直接进入后端。
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("cache key length exceeds " + MAX_KEY_LENGTH);
        }
        return new BuiltCacheKey<>(key, policy, valueType);
    }

    /**
     * 按命名空间策略处理标识符。
     *
     * @param policy 命名空间策略
     * @param identifier 原始标识符
     * @return 普通标识符或 SHA-256 哈希值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static String resolveIdentifier(NamespacePolicy policy, String identifier) {
        String validIdentifier = requireSegment(identifier);
        // 强制 TTL 的临时状态键必须隐藏原始敏感标识符。
        if (policy.isTtlRequired()) {
            return hash(validIdentifier);
        } else {
            // 普通可重建缓存保留可读标识符，便于运维定位。
            return validIdentifier;
        }
    }

    /**
     * 校验键片段。
     *
     * @param segment 键片段
     * @return 已校验片段
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static String requireSegment(String segment) {
        // 空片段或分隔符会破坏固定键结构，因此直接拒绝。
        if (segment == null || segment.isBlank() || segment.contains(":")) {
            throw new IllegalArgumentException("cache key segment must be non-blank and contain no colon");
        }
        // 超长片段在哈希替换前拒绝，避免超长键直接进入后端。
        if (segment.length() > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("cache key segment length exceeds " + MAX_SEGMENT_LENGTH);
        }
        return segment;
    }

    /**
     * 对敏感标识符执行 SHA-256 哈希。
     *
     * @param identifier 敏感标识符
     * @return 小写十六进制哈希值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static String hash(String identifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(identifier.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
