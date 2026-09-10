package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 后端中立的缓存信封序列化契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public interface CacheEnvelopeCodec {

    /**
     * 将类型化缓存信封编码为 JSON。
     *
     * @param envelope 类型化缓存信封
     * @param <T> 缓存值类型
     * @return JSON 字符串
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    <T> String encode(CacheEnvelope<T> envelope);

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
    <T> CacheEnvelope<T> decode(String serializedEnvelope, Class<T> valueType);
}
