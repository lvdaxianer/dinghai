package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 缓存键的命名空间、模块和资源路径。
 *
 * @param namespace 策略命名空间名称
 * @param module 模块名称
 * @param resource 资源名称
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record CacheKeyNamespace(String namespace, String module, String resource) {

    /**
     * 校验缓存键路径。
     *
     * @param namespace 策略命名空间名称
     * @param module 模块名称
     * @param resource 资源名称
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CacheKeyNamespace {
        namespace = requireName(namespace);
        module = requireKeySegment(module);
        resource = requireKeySegment(resource);
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
        // 空白命名空间无法关联显式策略，因此直接拒绝。
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return name;
    }

    /**
     * 校验 Redis 键片段。
     *
     * @param segment 键片段
     * @return 已校验片段
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static String requireKeySegment(String segment) {
        // 空片段或分隔符会破坏固定键结构，因此直接拒绝。
        if (segment == null || segment.isBlank() || segment.contains(":")) {
            throw new IllegalArgumentException("cache key segment must be non-blank and contain no colon");
        }
        // 超长片段直接拒绝，避免超长键直接进入后端。
        if (segment.length() > CacheKeyBuilder.MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "cache key segment length exceeds " + CacheKeyBuilder.MAX_SEGMENT_LENGTH);
        }
        return segment;
    }
}
