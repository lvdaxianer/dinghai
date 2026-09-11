package io.github.lvdaxianer.dinghai.sharedstate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

    /** 用于计算剩余有效期的单调时钟。 */
    private final Ticker ticker;

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
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(this.ticker)
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
     * 读取缓存值，未命中时在单进程内按键合并回源并写入。
     *
     * @param key    类型化缓存键
     * @param expiry 回源写入时的到期策略
     * @param loader 回源加载器，不得返回 null
     * @param <T>    缓存值类型
     * @return 缓存命中值或回源加载值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Override
    public <T> T getOrLoad(CacheKey<T> key, CacheExpiry expiry,
            Supplier<? extends T> loader) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        Supplier<? extends T> validLoader = Objects.requireNonNull(loader, "loader");
        // 先校验到期策略，非法策略在回源前快速失败，避免无谓加载。
        validateExpiry(validKey, expiry);
        // computeIfAbsent 在单进程内按键合并并发回源，同一键同一时刻 loader 至多执行一次。
        CaffeineStoredEntry entry = cache.asMap().computeIfAbsent(validKey.value(),
                ignored -> createEntry(validKey, CacheLoaderSupport.loadNonNull(validLoader),
                        expiry));
        return readValue(validKey, entry);
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

    @Override
    public <T> boolean compareAndDelete(CacheKey<T> key, T expectedValue) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expectedValue, "expectedValue");
        AtomicBoolean deleted = new AtomicBoolean();
        cache.asMap().computeIfPresent(validKey.value(), (ignored, entry) -> {
            if (Objects.equals(readValue(validKey, entry), expectedValue)) {
                deleted.set(true);
                return null;
            }
            return entry;
        });
        return deleted.get();
    }

    @Override
    public <T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry) {
        CaffeineStoredEntry entry = createEntry(key, value, expiry);
        CaffeineStoredEntry previous = cache.asMap().put(Objects.requireNonNull(key, "key").value(), entry);
        return Optional.ofNullable(previous).map(item -> readValue(key, item));
    }

    /**
     * 单进程内原子读取缓存值并按新到期策略重放写入。
     *
     * @param key 类型化缓存键
     * @param expiry 续期后的到期策略
     * @param <T> 缓存值类型
     * @return 续期前的缓存值，键不存在时为空且不创建键
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Override
    public <T> Optional<T> getAndExpire(CacheKey<T> key, CacheExpiry expiry) {
        CacheKey<T> validKey = Objects.requireNonNull(key, "key");
        validateExpiry(validKey, expiry);
        AtomicReference<CaffeineStoredEntry> previous = new AtomicReference<>();
        // computeIfPresent 保证读取与 TTL 重放写入在单进程内原子完成，键不存在时不创建。
        cache.asMap().computeIfPresent(validKey.value(), (ignored, entry) -> {
            previous.set(entry);
            return recreateEntry(validKey, entry, expiry);
        });
        return Optional.ofNullable(previous.get()).map(entry -> readValue(validKey, entry));
    }

    @Override
    public Optional<CacheExpiry> remainingTtl(CacheKey<?> key) {
        CaffeineStoredEntry entry = cache.getIfPresent(Objects.requireNonNull(key, "key").value());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isEternal()) {
            return Optional.of(CacheExpiry.eternal());
        }
        long elapsed = ticker.read() - entry.createdAtNanos();
        long remaining = entry.expiryNanos() - elapsed;
        return remaining > 0L ? Optional.of(CacheExpiry.after(Duration.ofNanos(remaining)))
                : Optional.empty();
    }

    @Override
    public boolean expire(CacheKey<?> key, CacheExpiry expiry) {
        validateExpiry(key, expiry);
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        AtomicBoolean updated = new AtomicBoolean();
        cache.asMap().computeIfPresent(validKey.value(), (ignored, entry) -> {
            updated.set(true);
            return recreateEntry(validKey, entry, expiry);
        });
        return updated.get();
    }

    @Override
    public boolean persist(CacheKey<?> key) {
        CacheKey<?> validKey = Objects.requireNonNull(key, "key");
        validateExpiry(validKey, CacheExpiry.eternal());
        return expire(validKey, CacheExpiry.eternal());
    }

    @Override
    public <T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys) {
        Map<CacheKey<T>, T> values = new LinkedHashMap<>(keys.size());
        for (CacheKey<T> key : keys) {
            get(key).ifPresent(value -> values.put(key, value));
        }
        return values;
    }

    @Override
    public <T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries) {
        entries.forEach((key, entry) -> put(key, entry.value(), entry.expiry()));
    }

    @Override
    public long deleteAll(Collection<? extends CacheKey<?>> keys) {
        return keys.stream().map(CacheKey::value).distinct()
                .filter(value -> cache.asMap().remove(value) != null).count();
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
        return new CaffeineStoredEntry(serializedEnvelope, expiry.toNanos(), ticker.read(),
                expiry.isEternal());
    }

    /** 使用新 TTL 重建保留原值的通配符键条目。 */
    private CaffeineStoredEntry recreateEntry(CacheKey<?> key, CaffeineStoredEntry entry,
            CacheExpiry expiry) {
        return recreateTypedEntry(key, entry, expiry);
    }

    /** 将通配符键收窄为可创建条目的类型化键。 */
    private <T> CaffeineStoredEntry recreateTypedEntry(CacheKey<T> key,
            CaffeineStoredEntry entry, CacheExpiry expiry) {
        return createEntry(key, readValue(key, entry), expiry);
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
