# dinghai (定海神针 — the sea-pinning needle)

> The magic staff that pins the sea — steadying your state.

English | [中文](README.md)

dinghai is lvdaxianer's personal open-source base-component library, distilled and extracted from production projects. It has zero business coupling and focuses on providing directly reusable infrastructure capabilities.

## Module List

| Module | Version | Description |
| --- | --- | --- |
| `dinghai-shared-state` | 1.0.0 | Shared state cache: dual Redis/Caffeine backends, circuit-breaker fault tolerance, observability, Spring Boot 3 auto-configuration |
| `dinghai-file-storage-core` | 1.0.0 | Backend-neutral storage contracts, object models, presigned models, and stable error codes |
| `dinghai-file-storage-local` | 1.0.0 | Local-disk adapter with Unicode/nested keys, HMAC presigning, and streaming I/O |
| `dinghai-file-storage-minio` | 1.0.0 | Fixed-bucket MinIO adapter with native presigning |
| `dinghai-file-storage-spring-boot-starter` | 1.0.0 | Provider auto-configuration and local Web presigned endpoints |

## AI Agent Skill

This repository ships with an Agent Skill file, [`.agents/skills/dinghai/SKILL.md`](.agents/skills/dinghai/SKILL.md), aimed at AI coding assistants that support the Agent Skill mechanism: it describes each module's Maven coordinates, core capabilities, operation cheat sheet, and usage patterns, so that the AI proactively suggests adopting this library for needs such as caching, shared state, session renewal, and read-through loading — instead of reinventing the wheel.

Usage:

- **Automatic discovery**: tools that support Agent Skills (such as Kimi Code) automatically scan `.agents/skills/` after cloning the repository and load the skill based on trigger words (cache, shared state, distributed counter, etc.);
- **Manual invocation**: in Kimi Code, run `/skill:dinghai` to load the Skill directly.

## dinghai-shared-state

A typed shared-state caching library providing backend-neutral contracts for caches and ephemeral state:

- **Dual backends**: Caffeine (single-process, local) and Redis (distributed, Lettuce client, supporting standalone and Sentinel modes)
- **Circuit-breaker fault tolerance**: automatically trips and degrades to local Caffeine when Redis fails, and automatically switches back after recovery
- **Observability**: built-in health snapshot and metrics snapshot providers
- **Auto-configuration**: works out of the box with Spring Boot 3 — no hand-written configuration classes required

Requirements: Java 21+, Spring Boot 3.x.

### Quick Start

Add the Maven coordinates:

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-shared-state</artifactId>
    <version>1.0.0</version>
</dependency>
```

Once the dependency is added, `SharedStateCacheConfiguration` takes effect via Spring Boot auto-configuration (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) and automatically registers the following Beans:

| Bean Name / Type | Wiring Condition |
| --- | --- |
| `cacheEnvelopeCodec` (`CacheEnvelopeCodec`) | An `ObjectMapper` Bean exists in the container, and the application has not provided a custom `CacheEnvelopeCodec` (the application may supply a custom implementation to replace the default Jackson codec) |
| `sharedStateRedisConnectionFactory` (`LettuceConnectionFactory`) | `RedisConfiguredCondition` is satisfied; independent of any other business `RedisConnectionFactory` |
| `sharedStateRedisOperations` (`RedisTemplate<String, String>`, uniform String serialization) | `RedisConfiguredCondition` is satisfied, and `sharedStateRedisConnectionFactory` exists |
| `redisSharedStateCache` (`RedisSharedStateCache`) | `RedisConfiguredCondition` is satisfied, and `sharedStateRedisOperations` exists |
| `caffeineSharedStateCache` (`CaffeineSharedStateCache`) | Unconditional |
| `sharedStateCache` (`ResilientSharedStateCache`, `@Primary`) | Unconditional |
| `sharedStateCacheHealthProvider` (`SharedStateCacheHealthProvider`) | Unconditional |
| `sharedStateCacheMetricsProvider` (`SharedStateCacheMetricsProvider`) | Unconditional |

Business code simply injects the `SharedStateCache` interface (the actual type is `ResilientSharedStateCache`).

### Configuration

The configuration prefix is `dinghai.cache`. All configuration keys are listed below:

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `dinghai.cache.caffeine.maximum-size` | `long` | `10000` | Maximum number of entries in the Caffeine backend; must be positive |
| `dinghai.cache.redis.mode` | `standalone` / `sentinel` | `standalone` | Redis connection mode; any other value is rejected before wiring |
| `dinghai.cache.redis.host` | `String` | none | Redis host in standalone mode; any non-blank value is treated as enabling Redis, blank falls back to Caffeine |
| `dinghai.cache.redis.port` | `int` | `6379` | Redis port in standalone mode; must be between 1 and 65535 |
| `dinghai.cache.redis.database` | `int` | `0` | Redis database index; must not be negative |
| `dinghai.cache.redis.password` | `String` | none | Redis server password; blank means no password |
| `dinghai.cache.redis.sentinel.master` | `String` | none | Sentinel master name; required and must not be blank in sentinel mode |
| `dinghai.cache.redis.sentinel.nodes` | `List<String>` | empty list | Sentinel node list (`host:port`, comma-separated supported); required and must not be empty in sentinel mode |
| `dinghai.cache.redis.sentinel.username` | `String` | none | Sentinel ACL username; ACL username authentication is enabled only when non-blank |
| `dinghai.cache.redis.sentinel.password` | `String` | none | Sentinel ACL password; set only when non-blank, to avoid overriding anonymous access |
| `dinghai.cache.circuit.failure-threshold` | `int` | `3` | Consecutive Redis failure threshold; the circuit opens once reached; must be positive |
| `dinghai.cache.circuit.open-duration` | `Duration` | `30s` | How long the circuit stays open; enters half-open probing on expiry; must be a positive duration |
| `dinghai.cache.circuit.warm-ttl` | `Duration` | `5m` | Validity period of locally warmed entries during degradation; must be a positive duration |

Minimal configuration example for standalone mode:

```yaml
dinghai:
  cache:
    redis:
      host: 127.0.0.1
      port: 6379
```

Configuration example for Sentinel mode:

```yaml
dinghai:
  cache:
    redis:
      mode: sentinel
      sentinel:
        master: mymaster
        nodes: 127.0.0.1:26379,127.0.0.2:26379
```

### Backend Selection Behavior

- **Redis not configured** (standalone `host` is blank, and sentinel mode is not explicitly declared): only the Caffeine backend is wired, and `sharedStateCache` goes directly to the local cache.
- **Redis explicitly configured** (`host` set in standalone mode, or `mode: sentinel` with complete sentinel configuration): the Redis backend is wired as the primary backend, wrapped by `ResilientSharedStateCache` for circuit-breaker fallback, degrading to Caffeine when Redis fails.
- **Not affected by other business Redis Beans**: whether shared-state Redis is enabled depends solely on the `dinghai.cache.redis.*` configuration itself (`RedisConfiguredCondition` / `isConfigured()`). When enabled, auto-configuration creates `sharedStateRedisConnectionFactory` and injects it explicitly into `sharedStateRedisOperations`; the application's business `RedisConnectionFactory` is not reused.

### Fault Tolerance and Observability

`ResilientSharedStateCache` has a built-in circuit state machine; see `SharedStateCircuitState` for the state values:

- `CLOSED`: Redis requests pass through normally;
- `OPEN`: the circuit opens after consecutive failures reach `failure-threshold`, and requests degrade to locally warmed Caffeine entries;
- `HALF_OPEN`: after `open-duration` expires, only a single recovery probe request is allowed through; on success the circuit returns to `CLOSED`.

Observability is exposed through two provider Beans, injectable into any Spring-managed component:

- `SharedStateCacheHealthProvider#getHealth()` returns a `SharedStateCacheHealth` health snapshot: `isRedisConfigured`, `circuitState`, `consecutiveFailures`, `activeBackend`, `isDegraded`;
- `SharedStateCacheMetricsProvider#getMetrics()` returns a `SharedStateCacheMetrics` metrics snapshot: `totals` for global aggregates and `dimensionCounts` for counts by event/namespace dimension, plus convenience accessors such as `redisFailureCount()`, `circuitOpenCount()`, `fallbackCount()`, `consistencyLossCount()`, `recoveryCount()`, `halfOpenCount()`, and `backendSelectionCount()`.

### Micrometer / Actuator Soft Integration

`micrometer-core` and `spring-boot-actuator` are declared as optional soft dependencies and are not forced onto the application. When the corresponding components are on the classpath, the following two auto-configuration classes (also registered in `AutoConfiguration.imports`, taking effect after `SharedStateCacheConfiguration`) are enabled automatically:

- **Micrometer metrics** (`SharedStateCacheMetricsAutoConfiguration`, condition: `MeterRegistry` on the classpath and its Bean present in the container; the default implementation backs off when the application declares a custom `SharedStateCacheMetricsBinder` Bean):
  - `dinghai.cache.circuit.state` (Gauge): circuit state, read directly from the health snapshot — `CLOSED=0`, `HALF_OPEN=1`, `OPEN=2`;
  - `dinghai.cache.events` (Counter, tagged by `event`): seven event types — `failure`, `circuit-open`, `fallback`, `consistency-loss`, `recovery`, `half-open`, `backend-selection`. Counters are incremented by the event subscription hooks of the observability component (seeded once from the metrics snapshot at wiring time, thereafter incremented only by events, never written back); the snapshot provider remains the single entry point for querying metrics.
- **Actuator health endpoint** (`SharedStateCacheHealthAutoConfiguration`, condition: `HealthIndicator` on the classpath; the default implementation backs off when the application declares a Bean of the same name): registers a `HealthIndicator` named `sharedStateCacheHealthIndicator` (shown as `sharedStateCache` in the health endpoint). Degradation is a workable state, so the status is always `UP`, and details carry all health snapshot fields: `isRedisConfigured`, `circuitState`, `consecutiveFailures`, `activeBackend`, `isDegraded`.

### Key Format

Cache keys uniformly follow the `dinghai:{module}:{resource}:{identifier}` format, built by `CacheKeyBuilder`:

- Key segments (module / resource / identifier) must not be empty and must not contain `:`;
- Each key segment must not exceed 128 characters, and the assembled full key must not exceed 512 characters; on overflow, key building throws `IllegalArgumentException` directly (length is measured on the raw segments, before any hash substitution check);
- When the namespace policy is **TTL-required** (`NamespacePolicy.isTtlRequired()`, e.g. `NamespacePolicy.stateFallback(...)`), the identifier is replaced by its SHA-256 lowercase hex hash, preventing ephemeral-state keys from leaking the raw sensitive identifier;
- Ordinary rebuildable caches keep the readable identifier for operational diagnosis.

`CacheKey` is a sealed interface that can only be created by `CacheKeyBuilder`, guaranteeing a uniform key structure.

### Usage Example

```java
import io.github.lvdaxianer.dinghai.sharedstate.CacheExpiry;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKey;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKeyBuilder;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKeyNamespace;
import io.github.lvdaxianer.dinghai.sharedstate.CacheValue;
import io.github.lvdaxianer.dinghai.sharedstate.CompareAndSetCommand;
import io.github.lvdaxianer.dinghai.sharedstate.NamespacePolicy;
import io.github.lvdaxianer.dinghai.sharedstate.NamespacePolicyRegistry;
import io.github.lvdaxianer.dinghai.sharedstate.SharedStateCache;

import java.time.Duration;
import java.util.Optional;

// Declare namespace policies and create the key builder (can be held as a singleton in the application)
NamespacePolicyRegistry registry = NamespacePolicyRegistry.of(
        NamespacePolicy.cacheFallback("profile-cache"),
        NamespacePolicy.stateFallback("auth-state"));
CacheKeyBuilder keyBuilder = new CacheKeyBuilder(registry);

// Inject the primary cache Bean (actual type is ResilientSharedStateCache)
// @Autowired SharedStateCache sharedStateCache;

// Ordinary cache: keeps the readable identifier; the key looks like dinghai:user:profile:u-1001
CacheKeyNamespace profileNs = new CacheKeyNamespace("profile-cache", "user", "profile");
CacheKey<String> profileKey = keyBuilder.build(profileNs, "u-1001", String.class);
sharedStateCache.put(profileKey, "{\"name\":\"ding\"}", CacheExpiry.after(Duration.ofMinutes(10)));
Optional<String> profile = sharedStateCache.get(profileKey);

// Read-through loading: returns the cached value on hit; on miss, calls the loader to fetch from the source and writes it back with the expiry policy
String loaded = sharedStateCache.getOrLoad(profileKey,
        CacheExpiry.after(Duration.ofMinutes(10)), () -> loadProfileFromDatabase("u-1001"));

// TTL-required ephemeral state: the identifier is stored as its SHA-256 hash
CacheKeyNamespace sessionNs = new CacheKeyNamespace("auth-state", "auth", "session");
CacheKey<String> sessionKey = keyBuilder.build(sessionNs, "raw-session-token", String.class);
sharedStateCache.putIfAbsent(sessionKey, "token-value", CacheExpiry.after(Duration.ofMinutes(30)));

// Atomic compare-and-set
boolean swapped = sharedStateCache.compareAndSet(sessionKey,
        new CompareAndSetCommand<>("token-value", "rotated-token",
                CacheExpiry.after(Duration.ofMinutes(30))));
```

Full `SharedStateCache` operation list (see the interface source for details):

| Method | Description |
| --- | --- |
| `<T> Optional<T> get(CacheKey<T> key)` | Read a typed cache value |
| `<T> void put(CacheKey<T> key, T value, CacheExpiry expiry)` | Insert or overwrite a cache value |
| `<T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry)` | Write only if the key does not exist |
| `<T> T getOrLoad(CacheKey<T> key, CacheExpiry expiry, Supplier<? extends T> loader)` | Read-through loading: returns directly on hit; on miss, loads from the source, writes back, then returns; a loader returning null throws `IllegalArgumentException` and nothing is written |
| `<T> Optional<T> getAndDelete(CacheKey<T> key)` | Atomically read and delete |
| `long increment(CacheKey<Long> key, long delta, CacheExpiry expiry)` | Atomically increment a long value |
| `<T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command)` | Atomic compare-and-update |
| `<T> boolean compareAndDelete(CacheKey<T> key, T expectedValue)` | Atomically delete when the value matches |
| `<T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry)` | Atomically read the old value and replace it with a new one |
| `<T> Optional<T> getAndExpire(CacheKey<T> key, CacheExpiry expiry)` | Atomically read the original value and reset the TTL to the given expiry policy (sliding renewal); returns empty and does not create the key when the key does not exist |
| `Optional<CacheExpiry> remainingTtl(CacheKey<?> key)` | Query the remaining TTL; empty for absent keys, `eternal()` for eternal keys |
| `boolean expire(CacheKey<?> key, CacheExpiry expiry)` | Set a TTL on an existing key |
| `boolean persist(CacheKey<?> key)` | Remove the TTL of a key in an ordinary namespace; TTL-required namespaces reject this |
| `<T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys)` | Batch read; returns only the keys that hit |
| `<T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries)` | Batch write with each `CacheValue`'s own TTL |
| `long deleteAll(Collection<? extends CacheKey<?>> keys)` | Batch delete; returns the number of unique keys actually deleted |
| `boolean delete(CacheKey<?> key)` | Delete a cache value |
| `boolean exists(CacheKey<?> key)` | Check whether a key exists |

Note: the consistency scope of atomic operations is declared by the backend; the Caffeine backend only guarantees single-process atomicity.

Interface evolution note: `SharedStateCache` gains new methods with new versions (e.g. `getOrLoad`, `getAndExpire`). Code that uses this interface via dependency injection is unaffected; code outside this repository that implements the interface directly must add implementations of the new methods after upgrading.

`getOrLoad` provides read-through loading: the loader is not invoked on a hit; on a miss, the loader fetches from the source and the result is written with the given `expiry`. The loader must not return null (otherwise `IllegalArgumentException` is thrown and nothing is written); exceptions thrown by the loader itself propagate directly and are not counted as circuit failures. The Caffeine backend merges concurrent loads per key within a single process (the loader executes at most once per key at any moment); the Redis backend executes in the order `get` → loader → `putIfAbsent` → re-read on conflict, so under concurrency the existing value wins, with no cross-process single-flight guarantee.

`CacheValue<T>` binds a batch-write value to its `CacheExpiry`. `putAll` validates the type and namespace TTL constraints of all entries before writing, preventing partial writes caused by caller argument errors; the Redis backend submits independent key writes in a single pipeline and verifies each execution result after commit — any failed entry throws `RedisBackendException` (fail-fast). `getAll` and `deleteAll` use native batch read and delete commands respectively. Note that batch operations do not guarantee atomicity; after a failure, the cache may be left in a partially written state.

In the TTL lifecycle, `remainingTtl` returns empty for absent keys and `CacheExpiry.eternal()` for eternal keys; `expire` and `persist` return `true` only when they succeed on an existing key. `persist` cannot be called on TTL-required namespaces, ensuring that ephemeral security state never becomes permanent. `getAndExpire` provides sliding renewal: it atomically reads the original value and resets the TTL to the given expiry policy; it returns empty and does not create the key when the key does not exist. Passing `CacheExpiry.eternal()` renews an ephemeral key to eternal (except in TTL-required namespaces), which suits session-style "renew on access" scenarios.

### Testing

Run all tests by default (no external dependencies required):

```bash
mvn test
```

The real-Redis contract test (`RedisSharedStateCacheContractTest`) is skipped by default; it must be explicitly enabled and the target Redis must be reachable:

```bash
mvn test -Ddinghai.redis.contract-test=true
```

The following system properties specify the contract test target (default values in parentheses):

| System Property | Description |
| --- | --- |
| `dinghai.redis.contract-host` (`127.0.0.1`) | Redis host in standalone mode |
| `dinghai.redis.contract-port` (`6379`) | Redis port in standalone mode |
| `dinghai.redis.contract-password` (empty) | Redis password |
| `dinghai.redis.contract-mode` (`standalone`) | Connection mode |
| `dinghai.redis.contract-sentinel-master` (`mymaster`) | Sentinel master name |
| `dinghai.redis.contract-sentinel-nodes` (`127.0.0.1:26379`) | Sentinel node list |
| `dinghai.redis.contract-sentinel-username` (empty) | Sentinel ACL username |
| `dinghai.redis.contract-sentinel-password` (empty) | Sentinel ACL password |

## dinghai-file-storage

The unified file-storage adapter supports local disk and MinIO. Application code depends on the `FileStorage` contract and can switch providers through configuration. The local provider accepts nested keys such as `1/1/1/1.png` and Unicode paths, and uses temporary files plus streaming I/O rather than loading whole objects into memory.

Add the Starter:

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-file-storage-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Example configuration, with sensitive values injected through the environment:

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: ${DINGHAI_FILE_STORAGE_BUCKET}
    storage-alias: documents-v1
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
      stable-id-key: ${DINGHAI_FILE_STORAGE_STABLE_ID_KEY}
    web:
      enabled: true
      path-prefix: /files
      direct-access: false
      read-buffer-size: 128KB
      max-concurrent-reads: 128
    cache:
      max-object-size: 50MB
      buffer-size: 128KB
      max-concurrent-fills: 4
      queue-capacity: 32
    upload:
      max-size: 100MB
```

The Starter registers `/files/upload/{*key}`, `/files/download/{*key}`, and `/files/preview/{*key}` by default. Upload and download URLs can be presigned; download signatures bind the `inline` or `attachment` response disposition. Preview/download responses stream object content and support Content-Type, ETag, Last-Modified, Cache-Control, Expires, 304, and single-range 206/416 responses. Streaming uses a dedicated bounded async executor instead of an unbounded default executor. Unsigned access is denied by default; direct access requires explicit enablement and an application `FileStorageAccessPolicy`.

In addition to stable `fileId` URLs, callers can use real object-key URLs `/files/read/key/{*key}`, `/files/read/key/preview/{*key}`, and `/files/read/key/download/{*key}`. These routes still require a valid presignature or host direct-access authorization. `web.read-buffer-size` and `cache.buffer-size` are per-copy buffers rather than whole-file allocations; the concurrency and queue settings bound active response reads, cache fills, and waiting fill tasks.

`bucket` is a required shared setting for every provider and must be a single path segment. Local
stores object key `1/1/1/1.png` at `/var/lib/app/files/documents/1/1/1/1.png`; MinIO stores the
same object as `documents / 1/1/1/1.png`. The MinIO subsection contains only `endpoint`,
`access-key`, and `secret-key`. Buckets are not created automatically, and there is no automatic
fallback between local disk and MinIO. Signing keys, access keys, and full presigned URLs are never
written to logs or error responses.

Private objects can use stable read URLs: `/files/read/v1/{id}`, with
`/files/preview/v1/{id}` and `/files/download/v1/{id}` for preview and download.
An ID depends only on `storage-alias + object key` and the active AES-SIV key. `storage-alias` does
not determine physical placement, so an ID stays
unchanged when an object moves from Local to MinIO. Rotating `stable-id-key` invalidates old
URLs. This route intentionally does not apply component-level user authorization; the embedding
platform authorizes access before the browser request.

When upgrading, move the existing provider-specific MinIO bucket value to the shared `bucket` setting. For
Local, move existing objects from `local.root / objectKey` to `local.root / bucket / objectKey`, or
export and import them through the offline migration package.

Every non-Local provider enables a metadata-validated secondary disk read cache by default; only
the Local/disk provider is disabled by default. On a cache miss below `cache.max-object-size`, the request streams directly
while a second provider stream is persisted asynchronously. At 00:00, complete replicas idle for
longer than `cache.idle-ttl` are deleted. Production deployments should set a safe `cache.root`,
for example `${DINGHAI_FILE_STORAGE_CACHE_ROOT}`. Set `cache.enabled` explicitly to override the
default: `true` enables caching for Local, while `false` disables it for non-Local providers.

All file-storage modules are grouped under `dinghai-file-storage/`: `core`, `local`, `minio`, and
`spring-boot-starter` retain their existing Maven artifactIds. The development-only
`dinghai-file-storage/benchmark` module performs concurrent uploads, cold reads, MinIO cache fills/
hot reads, and SHA-256 integrity checks against real files. It accepts `--provider`, `--file`,
`--concurrency`, and `--read-rounds`. For MinIO, provide endpoint, access key, secret key, and
bucket through the corresponding `DINGHAI_FILE_STORAGE_MINIO_*` environment variables. Each run
uses isolated object keys and prints phase duration, throughput, average/P95/P99 latency, and
errors without printing secrets.

Local was measured with `982-合0820.FBX` (412,202,320 bytes) using the documented parameters: four
concurrent uploads transferred 1.53 GiB with 1298 ms average / 1406 ms P95 latency at 1117 MiB/s;
eight concurrent cold reads transferred 3.07 GiB with 200 ms average / 230 ms P95 latency at
7765 MiB/s. SHA-256 verification passed for every operation and errors were zero. Operating-system
file caching materially affects the Local read figure, so it is not a raw physical-disk result.
MinIO measurements remain runtime-environment dependent and must be run with valid credentials;
they report upload, cold-read, cache-fill, and hot-read phases separately.

Offline migration processes server-directory packages and never uploads large files over HTTP.
Set `dinghai.file-storage.migration.enabled=true` and configure `export-root`, `import-root`,
`completed-root`, and `failed-root`. Export writes `manifest.ndjson` and `objects/` with SHA-256
verification. Successful imports move packages to completed; failed packages and `errors.ndjson`
move to failed. The `/files/migration/export` and `/files/migration/import` management endpoints
are registered only when the host provides `FileStorageMigrationAccessPolicy`, which must
authorize `EXPORT` and `IMPORT` separately.

## Release

Day-to-day builds (`mvn test` / `mvn package`) do not trigger signing or publishing plugins. To publish to Maven Central, use the `release` profile:

```bash
mvn -Prelease clean deploy
```

Prerequisites for releasing:

- A GPG signing key is configured on the local machine (release artifacts are mandatorily signed; for local artifact verification you can skip with `-Dgpg.skip=true`);
- `settings.xml` contains a server with id `central` carrying the Central Portal user token;
- The publishing plugin is `central-publishing-maven-plugin` with `autoPublish=false`; after upload, the release must be confirmed manually in the Central Portal.

## License

This project is open-sourced under the [MIT License](LICENSE).

---

This English version mirrors the Chinese README; in case of any divergence, the Chinese version prevails.
