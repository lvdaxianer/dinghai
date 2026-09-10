package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * 基于 Jackson 的类型化缓存信封 JSON 编解码器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class JacksonCacheEnvelopeCodec implements CacheEnvelopeCodec {

    /** 应用统一 Jackson 对象映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Jackson 缓存信封编解码器。
     *
     * @param objectMapper 应用统一对象映射器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public JacksonCacheEnvelopeCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 将类型化缓存信封编码为 JSON。
     *
     * @param envelope 类型化缓存信封
     * @param <T> 缓存值类型
     * @return JSON 字符串
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> String encode(CacheEnvelope<T> envelope) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNull(envelope, "envelope"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cache envelope JSON encoding failed", exception);
        }
    }

    /**
     * 将 JSON 解码为指定值类型的缓存信封。
     *
     * @param serializedEnvelope JSON 字符串
     * @param valueType 缓存值类型
     * @param <T> 缓存值类型
     * @return 类型化缓存信封
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> CacheEnvelope<T> decode(String serializedEnvelope, Class<T> valueType) {
        JavaType envelopeType = createEnvelopeType(valueType);
        try {
            return objectMapper.readValue(serializedEnvelope, envelopeType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cache envelope JSON decoding failed", exception);
        }
    }

    /**
     * 创建包含实际缓存值类型的 Jackson 类型。
     *
     * @param valueType 缓存值类型
     * @param <T> 缓存值类型
     * @return Jackson 参数化类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> JavaType createEnvelopeType(Class<T> valueType) {
        Class<T> validValueType = Objects.requireNonNull(valueType, "valueType");
        return objectMapper.getTypeFactory().constructParametricType(CacheEnvelope.class, validValueType);
    }
}
