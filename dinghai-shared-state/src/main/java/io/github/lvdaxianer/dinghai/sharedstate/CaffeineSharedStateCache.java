package io.github.lvdaxianer.dinghai.sharedstate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Caffeine 的单进程共享状态后端。
 *
 * <p>CRUD 和复合操作只保证当前 JVM 内原子性，不提供跨节点一致性。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class CaffeineSharedStateCache implements SharedStateCache {

    /** Caffeine 条目缓存。 */
    private final Cache<String, CaffeineStoredEntry> cache;

    /** 缓存信封 JSON 编解码器。 */
    private final CacheEnvelopeCodec envelopeCodec;

    /**
     * 使用系统时钟创建 Caffeine 后端。
     *
     * @param maximumSize 最大缓存条目数
     * @param envelopeCodec 缓存信封 JSON 编解码器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CaffeineSharedStateCache(long maximumSize, CacheEnvelopeCodec envelopeCodec) {
        this(maximumSize, Ticker.systemTicker(), envelopeCodec);
    }

    /**
     * 使用指定时钟创建 Caffeine 后端。
     *
     * @param maximumSize 最大缓存条目数
     * @param ticker      Caffeine 时钟
     * @param envelopeCodec 缓存信封 JSON 编解码器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CaffeineSharedStateCache(long maximumSize, Ticker ticker,
            CacheEnvelopeCodec envelopeCodec) {
        // 非正数容量无法提供有效缓存，因此配置阶段直接拒绝。
        if (maximumSize <= 0L) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(Objects.requireNonNull(ticker, "ticker"))
                .expireAfter(new CaffeineStoredEntryExpiry())
                .build();
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
    }

    /**
     * 读取类型化缓存值。
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
        CaffeineStoredEntry entry = cache.getIfPresent(validKey.value());
        return Optional.ofNullable(entry).map(value -> readValue(validKey, value));
    }

    /**
     * 新增或覆盖缓存值。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> void put(CacheKey<T> key, T value, CacheExpiry expiry) {
        cache.put(key.value(), createEntry(key, value, expiry));
    }

    /**
     * 仅在键不存在时写入缓存值。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @return true 表示写入成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry) {
        CaffeineStoredEntry entry = createEntry(key, value, expiry);
        return cache.asMap().putIfAbsent(key.value(), entry) == null;
    }

    /**
     * 原子读取并删除缓存值。
     *
     * @param key 类型化缓存键
     * @param <T> 缓存值类型
     * @return 删除前的缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> Optional<T> getAndDelete(CacheKey<T> key) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        CaffeineStoredEntry removed = cache.asMap().remove(validKey.value());
        return Optional.ofNullable(removed).map(value -> readValue(validKey, value));
    }

    /**
     * 原子增加长整型缓存值。
     *
     * @param key    长整型缓存键
     * @param delta  增量
     * @param expiry 到期策略
     * @return 增加后的值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long increment(CacheKey<Long> key, long delta, CacheExpiry expiry) {
        validateExpiry(key, expiry);
        AtomicLong result = new AtomicLong();
        cache.asMap().compute(key.value(), (ignored, current) -> {
            long currentValue = current == null ? 0L : readValue(key, current);
            long updatedValue = Math.addExact(currentValue, delta);
            result.set(updatedValue);
            return createEntry(key, updatedValue, expiry);
        });
        return result.get();
    }

    /**
     * 原子比较并更新缓存值。
     *
     * @param key           类型化缓存键
     * @param command       比较并更新命令
     * @param <T>           缓存值类型
     * @return true 表示更新成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public <T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command) {
        CompareAndSetCommand<T> validCommand = Objects.requireNonNull(command, "command");
        validateWrite(key, validCommand.newValue(), validCommand.expiry());
        AtomicBoolean updated = new AtomicBoolean();
        cache.asMap().computeIfPresent(key.value(), (ignored, current) -> {
            // 当前值匹配时期望更新并重置该条目的 TTL。
            if (Objects.equals(readValue(key, current), validCommand.expectedValue())) {
                updated.set(true);
                return createEntry(key, validCommand.newValue(), validCommand.expiry());
            } else {
                // 当前值不匹配时保持原条目和剩余 TTL 不变。
                return current;
            }
        });
        return updated.get();
    }

    /**
     * 删除缓存值。
     *
     * @param key 缓存键
     * @return true 表示删除成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean delete(CacheKey<?> key) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        return cache.asMap().remove(validKey.value()) != null;
    }

    /**
     * 判断缓存键是否存在。
     *
     * @param key 缓存键
     * @return true 表示存在
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public boolean exists(CacheKey<?> key) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        return cache.getIfPresent(validKey.value()) != null;
    }

    /**
     * 失效当前进程中的全部本地镜像。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 创建已校验的缓存条目。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @return Caffeine 条目
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> CaffeineStoredEntry createEntry(CacheKey<T> key, T value, CacheExpiry expiry) {
        validateWrite(key, value, expiry);
        String serializedEnvelope = envelopeCodec.encode(CacheEnvelope.of(key.valueType(), value));
        return new CaffeineStoredEntry(serializedEnvelope, expiry.toNanos());
    }

    /**
     * 校验写入参数和类型。
     *
     * @param key    类型化缓存键
     * @param value  缓存值
     * @param expiry 到期策略
     * @param <T>    缓存值类型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> void validateWrite(CacheKey<T> key, T value, CacheExpiry expiry) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        Object validValue = Objects.requireNonNull(value, "value");
        // 声明类型与实际类型不一致时拒绝写入，避免后续读取失败。
        if (!validKey.valueType().isInstance(validValue)) {
            throw new IllegalArgumentException("cache value does not match declared type");
        }
        validateExpiry(validKey, expiry);
    }

    /**
     * 校验命名空间到期策略。
     *
     * @param key    缓存键
     * @param expiry 到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void validateExpiry(CacheKey<?> key, CacheExpiry expiry) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        CacheExpiry validExpiry = Objects.requireNonNull(expiry, "expiry");
        // 临时命名空间禁止永久条目，以免安全状态无限期残留。
        if (validKey.namespace().isTtlRequired() && validExpiry.isEternal()) {
            throw new IllegalArgumentException("namespace requires a positive ttl: "
                    + validKey.namespace().name());
        }
    }

    /**
     * 从信封读取并校验类型化值。
     *
     * @param key   类型化缓存键
     * @param entry Caffeine 条目
     * @param <T>   缓存值类型
     * @return 类型化缓存值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private <T> T readValue(CacheKey<T> key, CaffeineStoredEntry entry) {
        CacheEnvelope<T> envelope = envelopeCodec.decode(entry.serializedEnvelope(), key.valueType());
        validateEnvelope(key, envelope);
        return envelope.value();
    }

    /**
     * 校验信封版本和类型。
     *
     * @param key      类型化缓存键
     * @param envelope 缓存值信封
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void validateEnvelope(CacheKey<?> key, CacheEnvelope<?> envelope) {
        // 不支持的模式版本应显式失败，避免误读未来格式。
        if (envelope.schemaVersion() != CacheEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("unsupported cache envelope schema version");
        }
        // 类型标识或实际值不匹配时显式失败，避免未经检查的类型转换。
        if (!key.valueType().getName().equals(envelope.typeIdentifier())
                || !key.valueType().isInstance(envelope.value())) {
            throw new IllegalStateException("cache envelope type mismatch");
        }
    }

}
