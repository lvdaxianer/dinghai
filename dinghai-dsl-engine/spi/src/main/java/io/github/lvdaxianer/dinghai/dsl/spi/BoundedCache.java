package io.github.lvdaxianer.dinghai.dsl.spi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 有界、TTL 和 LRU 组合缓存实现。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
final class BoundedCache<K, V> {

    private final int maximumSize;
    private final Duration ttl;
    private final Clock clock;
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    BoundedCache(int maximumSize, Duration ttl) {
        this(maximumSize, ttl, Clock.systemUTC());
    }

    BoundedCache(int maximumSize, Duration ttl, Clock clock) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must not be negative");
        }
        this.maximumSize = maximumSize;
        this.ttl = ttl;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (expired(entry.createdAt())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    synchronized void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.instant()));
        evictIfNeeded();
    }

    synchronized void invalidateAll() {
        entries.clear();
    }

    synchronized int size() {
        removeExpired();
        return entries.size();
    }

    private boolean expired(Instant createdAt) {
        return !clock.instant().isBefore(createdAt.plus(ttl));
    }

    private void evictIfNeeded() {
        Iterator<K> iterator = entries.keySet().iterator();
        while (entries.size() > maximumSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private void removeExpired() {
        entries.entrySet().removeIf(entry -> expired(entry.getValue().createdAt()));
    }

    private record Entry<V>(V value, Instant createdAt) {
    }
}
