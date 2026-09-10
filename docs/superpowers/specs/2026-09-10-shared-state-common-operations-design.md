# Shared State Common Operations Design

## Scope

Extend `dinghai-shared-state` with conditions-safe deletion and replacement, TTL inspection and lifecycle control, and bulk access. The public API remains backend-neutral and is implemented consistently by Redis, Caffeine, and the resilient facade.

## Public API

`SharedStateCache` will add `compareAndDelete`, `getAndSet`, `remainingTtl`, `expire`, `persist`, `getAll`, `putAll`, and `deleteAll`. `CacheValue<T>` represents a batch entry so every key has an independent value and `CacheExpiry`.

TTL lookup returns `Optional<CacheExpiry>`: empty for an absent key, `CacheExpiry.eternal()` for a persistent key, and a positive TTL for a live expiring key. `persist` rejects namespaces that require TTL.

## Backend Semantics

Redis implements compare-delete and get-set in Lua. It uses `PTTL`, `MGET`, `DEL`, and grouped pipelined writes for the remaining new operations. Caffeine uses atomic `asMap()` operations and absolute expiry stored in each entry. Both preserve the existing key and envelope formats.

The resilient facade runs every new operation through `SharedStateCacheExecution`. Redis-success paths update or invalidate local mirrors; a TTL mirror uses Redis's remaining TTL instead of the configured warm TTL. Redis failures retain existing fallback and fail-closed namespace behavior.

## Auto-Configuration

The shared-state Redis connection will be a named `sharedStateRedisConnectionFactory`, created solely from `dinghai.cache.redis.*`. The shared Redis template receives it by qualifier, preventing accidental reuse of an unrelated application Redis factory.

## Verification

Shared contract tests cover all three implementations. Redis unit and integration tests cover scripts, PTTL mapping and grouped batch operations. Caffeine tests use a mutable ticker for expiry. Resilience tests exercise fallback, recovery, and mirror invalidation. Configuration tests assert connection-factory identity and configuration isolation.
