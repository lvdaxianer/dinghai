package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 携带模式版本和类型标识的缓存值信封。
 *
 * <p>该结构可直接序列化为 JSON，后端据此执行版本和类型校验。</p>
 *
 * @param schemaVersion  模式版本
 * @param typeIdentifier 声明类型标识
 * @param value          缓存值
 * @param <T>            缓存值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public record CacheEnvelope<T>(int schemaVersion, String typeIdentifier, T value) {

    /** 当前缓存信封模式版本。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * 校验缓存值信封。
     *
     * @param schemaVersion 模式版本
     * @param typeIdentifier 声明类型标识
     * @param value 缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CacheEnvelope {
        // 非法版本无法可靠反序列化，因此构造时直接拒绝。
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        // 类型标识缺失时无法执行类型安全读取，因此直接拒绝。
        if (typeIdentifier == null || typeIdentifier.isBlank()) {
            throw new IllegalArgumentException("typeIdentifier must not be blank");
        }
        value = Objects.requireNonNull(value, "value");
    }

    /**
     * 使用当前模式版本创建类型化信封。
     *
     * @param valueType 声明值类型
     * @param value     缓存值
     * @param <T>       缓存值类型
     * @return 类型化缓存值信封
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static <T> CacheEnvelope<T> of(Class<T> valueType, T value) {
        Objects.requireNonNull(valueType, "valueType");
        return new CacheEnvelope<>(CURRENT_SCHEMA_VERSION, valueType.getName(), value);
    }
}
