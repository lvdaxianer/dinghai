package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 基于 Spring Data Redis 的共享状态后端。
 *
 * <p>普通写入使用 Redis 原生命令，比较更新和计数器使用 Lua 保证跨节点原子性。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class RedisSharedStateCache implements SharedStateCache {

    /** Lua 脚本更新成功的返回码。 */
    private static final Long LUA_SUCCESS_CODE = 1L;

    /** Redis 操作模板。 */
    private final RedisOperations<String, String> operations;

    /** 缓存信封编解码器。 */
    private final CacheEnvelopeCodec envelopeCodec;

    /**
     * 创建 Redis 共享状态后端。
     *
     * @param operations Redis 操作模板
     * @param envelopeCodec 缓存信封编解码器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public RedisSharedStateCache(RedisOperations<String, String> operations,
            CacheEnvelopeCodec envelopeCodec) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
    }

    /**
     * 读取 Redis 类型化值。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> Optional<T> get(CacheKey<T> key) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        String value = RedisBackendOperations.call(
                () -> valueOperations().get(validKey.value()));
        return Optional.ofNullable(value).map(ignored -> decode(validKey, value));
    }

    /**
     * 写入或覆盖 Redis 值。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param expiry 到期策略
     * @param <T> 缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> void put(CacheKey<T> key, T value, CacheExpiry expiry) {
        validateWrite(key, value, expiry);
        String encoded = encode(key, value);
        RedisBackendOperations.run(() -> set(key.value(), encoded, expiry));
    }

    /**
     * 仅在 Redis 键不存在时写入值。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param expiry 到期策略
     * @param <T> 缓存值类型
     * @return true 表示写入成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry) {
        validateWrite(key, value, expiry);
        String encoded = encode(key, value);
        Boolean result = RedisBackendOperations.call(() -> expiry.isEternal()
                ? valueOperations().setIfAbsent(key.value(), encoded)
                : valueOperations().setIfAbsent(key.value(), encoded, expiration(expiry)));
        return Boolean.TRUE.equals(result);
    }

    /**
     * 原子读取并删除 Redis 值。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 删除前的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> Optional<T> getAndDelete(CacheKey<T> key) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        String value = RedisBackendOperations.call(
                () -> operations.execute(RedisLuaScripts.GET_AND_DELETE,
                        List.of(validKey.value())));
        return Optional.ofNullable(value).map(ignored -> decode(validKey, value));
    }

    /**
     * 使用 Lua 原子递增长整型值。
     *
     * @param key 长整型缓存键
     * @param delta 增量
     * @param expiry 到期策略
     * @return 增加后的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long increment(CacheKey<Long> key, long delta, CacheExpiry expiry) {
        validateExpiry(key, expiry);
        String initial = encode(key, 0L);
        String result = RedisBackendOperations.call(() -> operations.execute(
                RedisLuaScripts.INCREMENT, List.of(key.value()),
                String.valueOf(delta), initial, ttlMillis(expiry)));
        return Long.parseLong(Objects.requireNonNull(result, "increment result"));
    }

    /**
     * 使用 Lua 原子比较并更新值。
     *
     * @param key 类型化缓存键
     * @param command 比较并更新命令
     * @param <T> 缓存值类型
     * @return true 表示更新成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command) {
        CompareAndSetCommand<T> validCommand = Objects.requireNonNull(command, "command");
        validateWrite(key, validCommand.newValue(), validCommand.expiry());
        String expected = encode(key, validCommand.expectedValue());
        String updated = encode(key, validCommand.newValue());
        Long result = RedisBackendOperations.call(() -> operations.execute(
                RedisLuaScripts.COMPARE_AND_SET, List.of(key.value()),
                expected, updated, ttlMillis(validCommand.expiry())));
        return LUA_SUCCESS_CODE.equals(result);
    }

    /**
     * 删除 Redis 值。
     *
     * @param key 缓存键
     * @return true 表示删除了现有值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean delete(CacheKey<?> key) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        return Boolean.TRUE.equals(RedisBackendOperations.call(
                () -> operations.delete(validKey.value())));
    }

    /**
     * 判断 Redis 键是否存在。
     *
     * @param key 缓存键
     * @return true 表示存在
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean exists(CacheKey<?> key) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        return Boolean.TRUE.equals(RedisBackendOperations.call(
                () -> operations.hasKey(validKey.value())));
    }

    /**
     * 获取 Redis 值操作对象。
     *
     * @return Redis 值操作对象
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private ValueOperations<String, String> valueOperations() {
        return operations.opsForValue();
    }

    /**
     * 设置带 TTL 或永久有效的 Redis 值。
     *
     * @param key Redis 键
     * @param value 已编码值
     * @param expiry 到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void set(String key, String value, CacheExpiry expiry) {
        // 永久值不携带 Redis TTL；临时值使用调用方声明的到期时间。
        if (expiry.isEternal()) {
            valueOperations().set(key, value);
        } else {
            valueOperations().set(key, value, expiry.ttl());
        }
    }

    /**
     * 构建 Redis 原生过期参数。
     *
     * @param expiry 到期策略
     * @return Redis 过期时长
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private Duration expiration(CacheExpiry expiry) {
        return expiry.ttl();
    }

    /**
     * 转换为 Lua 使用的毫秒 TTL。
     *
     * @param expiry 到期策略
     * @return 毫秒 TTL，永久有效时返回零
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private String ttlMillis(CacheExpiry expiry) {
        // Lua 约定零表示永久有效，正 TTL 至少按一毫秒执行。
        if (expiry.isEternal()) {
            return "0";
        }
        long millis = expiry.ttl().toMillis();
        return String.valueOf(Math.max(1L, millis));
    }

    /**
     * 编码类型化缓存信封。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param <T> 缓存值类型
     * @return JSON 信封
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> String encode(CacheKey<T> key, T value) {
        return envelopeCodec.encode(CacheEnvelope.of(key.valueType(), value));
    }

    /**
     * 解码并校验类型化缓存信封。
     *
     * @param key 类型化缓存键
     * @param value JSON 信封
     * @param <T> 缓存值类型
     * @return 缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> T decode(CacheKey<T> key, String value) {
        CacheEnvelope<T> envelope = envelopeCodec.decode(value, key.valueType());
        // 同时校验模式版本、声明类型和实际值，避免读取错误命名空间的数据。
        if (envelope.schemaVersion() != CacheEnvelope.CURRENT_SCHEMA_VERSION
                || !key.valueType().getName().equals(envelope.typeIdentifier())
                || !key.valueType().isInstance(envelope.value())) {
            throw new IllegalStateException("cache envelope type mismatch");
        }
        return envelope.value();
    }

    /**
     * 校验写入值、类型和 TTL。
     *
     * @param key 类型化缓存键
     * @param value 缓存值
     * @param expiry 到期策略
     * @param <T> 缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> void validateWrite(CacheKey<T> key, T value, CacheExpiry expiry) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        // 类型化键和值必须一致，否则会破坏跨后端信封契约。
        if (!validKey.valueType().isInstance(Objects.requireNonNull(value, "value"))) {
            throw new IllegalArgumentException("cache value does not match declared type");
        }
        validateExpiry(validKey, expiry);
    }

    /**
     * 校验命名空间的 TTL 要求。
     *
     * @param key 缓存键
     * @param expiry 到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void validateExpiry(CacheKey<?> key, CacheExpiry expiry) {
        CacheExpiry validExpiry = Objects.requireNonNull(expiry, "expiry");
        // 临时状态命名空间必须设置 TTL，禁止形成无法自动清理的状态。
        if (Objects.requireNonNull(key, "key").namespace().isTtlRequired()
                && validExpiry.isEternal()) {
            throw new IllegalArgumentException("namespace requires a positive ttl: "
                    + key.namespace().name());
        }
    }
}
