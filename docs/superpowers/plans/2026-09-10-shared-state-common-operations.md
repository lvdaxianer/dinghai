# Shared State Common Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe single-key, TTL, and bulk shared-cache operations with consistent Caffeine, Redis, resilient-facade, and auto-configuration behavior.

**Architecture:** Extend the backend-neutral `SharedStateCache` contract and implement it in Caffeine first, then Redis, then the resilient facade. Redis uses Lua only where a multi-command race must be eliminated; bulk independent-key operations use Redis native batch primitives. The Spring configuration owns a named shared Redis connection factory so it cannot bind a business factory by accident.

**Tech Stack:** Java 21, JUnit 4, Mockito, Caffeine 2.9, Spring Data Redis, Lettuce, Jackson, Maven, OpenSpec.

---

### Task 1: Public Contract and All Implementations

**Files:**
- Create: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/CacheValue.java`
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/SharedStateCache.java`
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/CaffeineStoredEntry.java`
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/CaffeineSharedStateCache.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/CaffeineSharedStateCacheTest.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/SharedStateCacheContract.java`

- [ ] **Step 1: Write failing Caffeine tests for the new single-key and TTL API**

```java
Assert.assertTrue(cache.compareAndDelete(key, "before"));
Assert.assertFalse(cache.exists(key));
Assert.assertEquals(Optional.of("before"),
        cache.getAndSet(key, "after", CacheExpiry.after(Duration.ofSeconds(10))));
Assert.assertTrue(cache.expire(key, CacheExpiry.after(Duration.ofSeconds(5))));
Assert.assertTrue(cache.persist(key));
Assert.assertEquals(Optional.of(CacheExpiry.eternal()), cache.remainingTtl(key));
```

- [ ] **Step 2: Run the focused test to verify RED**

Run: `mvn -q -pl dinghai-shared-state -Dtest=CaffeineSharedStateCacheTest test`
Expected: compilation failure because the new `SharedStateCache` methods do not exist.

- [ ] **Step 3: Add the contract, value object, and compatible implementations in one atomic change**

```java
public record CacheValue<T>(T value, CacheExpiry expiry) {
    public CacheValue {
        value = Objects.requireNonNull(value, "value");
        expiry = Objects.requireNonNull(expiry, "expiry");
    }
}

<T> boolean compareAndDelete(CacheKey<T> key, T expectedValue);
<T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry);
Optional<CacheExpiry> remainingTtl(CacheKey<?> key);
boolean expire(CacheKey<?> key, CacheExpiry expiry);
boolean persist(CacheKey<?> key);
<T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys);
<T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries);
long deleteAll(Collection<? extends CacheKey<?>> keys);
```

Store `isEternal` and absolute `expiryNanos` in `CaffeineStoredEntry`; use `cache.asMap().compute` or `remove(key, value)` for atomic map transitions. In the same commit, add Redis Lua/native batch behavior and route all new calls through the resilient facade, so the expanded public interface never leaves a non-compiling implementation on `main`.

- [ ] **Step 4: Run the focused test to verify GREEN**

Run: `mvn -q -pl dinghai-shared-state -Dtest=CaffeineSharedStateCacheTest test`
Expected: all Caffeine cache tests pass.

- [ ] **Step 5: Add and run shared contract coverage for batch and namespace edge cases**

Add tests for absent/persistent/expiring TTL, independent batch TTLs, duplicate deletion keys, type mismatch, and `persist` rejection for a TTL-required namespace.
Run: `mvn -q -pl dinghai-shared-state -Dtest=CaffeineSharedStateCacheTest,ResilientLocalSharedStateCacheContractTest test`
Expected: tests compile and pass after facade changes are completed; until then only Caffeine tests are expected to compile.

### Task 2: Redis Atomic and Lifecycle Operations

**Files:**
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/RedisLuaScripts.java`
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCache.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCacheTest.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCacheContractTest.java`

- [ ] **Step 1: Write failing Redis unit tests**

```java
when(operations.execute(eq(RedisLuaScripts.COMPARE_AND_DELETE), eq(List.of(key.value())),
        anyString())).thenReturn(1L);
Assert.assertTrue(cache.compareAndDelete(key, "before"));
when(operations.getExpire(key.value(), TimeUnit.MILLISECONDS)).thenReturn(5000L);
Assert.assertTrue(cache.remainingTtl(key).orElseThrow().ttl().toMillis() <= 5000L);
```

- [ ] **Step 2: Run the focused test to verify RED**

Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheTest test`
Expected: compilation failure for unimplemented new methods and Lua constants.

- [ ] **Step 3: Implement Redis scripts and lifecycle methods**

Add `COMPARE_AND_DELETE` and `GET_AND_SET` scripts. Map `getExpire(..., MILLISECONDS)` values `-2` to empty and `-1` to eternal. Use `operations.expire`, `operations.persist`, and existing value encoding/validation without changing envelope fields.

- [ ] **Step 4: Run focused unit tests to verify GREEN**

Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheTest test`
Expected: all Redis mock tests pass.

- [ ] **Step 5: Extend the real Redis contract test**

Test cross-client compare-delete and get-set atomicity, PTTL mapping, `expire`, `persist`, and TTL-required namespace rejection.
Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheContractTest test`
Expected: skipped unless the Redis contract test is explicitly enabled and reachable; enabled runs pass against Redis.

### Task 3: Redis Bulk Operations

**Files:**
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCache.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCacheTest.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/RedisSharedStateCacheContractTest.java`

- [ ] **Step 1: Write failing unit tests for `MGET`, grouped writes, and one `DEL`**

```java
Assert.assertEquals(Map.of(firstKey, "first"), cache.getAll(List.of(firstKey, missingKey)));
cache.putAll(Map.of(firstKey, new CacheValue<>("first", CacheExpiry.eternal()),
        secondKey, new CacheValue<>("second", CacheExpiry.after(Duration.ofSeconds(5)))));
Assert.assertEquals(2L, cache.deleteAll(List.of(firstKey, firstKey, secondKey)));
```

- [ ] **Step 2: Run the focused test to verify RED**

Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheTest test`
Expected: failure because batch methods are absent.

- [ ] **Step 3: Implement independent-key bulk operations**

Use one `multiGet` call for deduplicated keys, one `delete(Collection<String>)` call for deduplicated deletes, and `RedisOperations.executePipelined` for `putAll`; preserve each `CacheValue` expiry and validate all entries before opening the pipeline.

- [ ] **Step 4: Run focused test to verify GREEN**

Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheTest test`
Expected: all Redis mock tests pass with assertions that no per-key remote calls occur.

- [ ] **Step 5: Add real Redis batch contract tests**

Verify only hits are returned, permanent and temporary entries retain distinct TTLs, and duplicate input keys are counted once.
Run: `mvn -q -pl dinghai-shared-state -Dtest=RedisSharedStateCacheContractTest test`
Expected: skip or pass according to contract-test availability.

### Task 4: Resilient Facade and Mirror Coordination

**Files:**
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/ResilientSharedStateCache.java`
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/SharedStateCacheExecution.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/ResilientSharedStateCacheTest.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/ResilientSharedStateCacheMirrorTest.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/ResilientLocalSharedStateCacheContractTest.java`

- [ ] **Step 1: Write failing resilient tests**

```java
when(redis.remainingTtl(key)).thenReturn(Optional.of(CacheExpiry.after(Duration.ofSeconds(3))));
cache.get(key);
Assert.assertEquals(Optional.of("value"), caffeine.get(key));
Assert.assertTrue(cache.compareAndDelete(key, "value"));
```

- [ ] **Step 2: Run focused tests to verify RED**

Run: `mvn -q -pl dinghai-shared-state -Dtest=ResilientSharedStateCacheTest,ResilientSharedStateCacheMirrorTest test`
Expected: compilation failure until the facade implements the expanded interface.

- [ ] **Step 3: Route every new method through the execution coordinator**

Use operation plans for each single-key and batch operation. On Redis success mirror confirmed values using actual remaining TTL, delete mirrors when a Redis result is absent or cannot establish freshness, and keep the existing fail-closed policy for distributed-required namespaces.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `mvn -q -pl dinghai-shared-state -Dtest=ResilientSharedStateCacheTest,ResilientSharedStateCacheMirrorTest,ResilientLocalSharedStateCacheContractTest test`
Expected: all resilience and local contract tests pass.

- [ ] **Step 5: Add race and fallback regression cases**

Cover Redis exceptions in each operation family, circuit-open local routing, recovery mirror invalidation, and batch result behavior.
Run: `mvn -q -pl dinghai-shared-state -Dtest=SharedStateCacheExecutionRaceTest,ResilientSharedStateCacheTest test`
Expected: all tests pass.

### Task 5: Dedicated Redis Auto-Configuration and Documentation

**Files:**
- Modify: `dinghai-shared-state/src/main/java/io/github/lvdaxianer/dinghai/sharedstate/SharedStateCacheConfiguration.java`
- Modify: `dinghai-shared-state/src/test/java/io/github/lvdaxianer/dinghai/sharedstate/SharedStateCacheConfigurationTest.java`
- Modify: `README.md`

- [ ] **Step 1: Write failing configuration identity tests**

```java
RedisTemplate<String, String> shared = context.getBean("sharedStateRedisOperations", RedisTemplate.class);
LettuceConnectionFactory dedicated = context.getBean("sharedStateRedisConnectionFactory",
        LettuceConnectionFactory.class);
Assert.assertSame(dedicated, shared.getConnectionFactory());
Assert.assertNotSame(externalRedisConnectionFactory, shared.getConnectionFactory());
```

- [ ] **Step 2: Run the focused test to verify RED**

Run: `mvn -q -pl dinghai-shared-state -Dtest=SharedStateCacheConfigurationTest test`
Expected: failure because the existing conditional factory is omitted when an external factory exists.

- [ ] **Step 3: Add the named dedicated factory and qualified template dependency**

Name the bean `sharedStateRedisConnectionFactory`, remove the global missing-factory condition, and inject it into `sharedStateRedisOperations` using `@Qualifier`. Retain the existing configuration validation and standalone/sentinel setup.

- [ ] **Step 4: Run configuration tests to verify GREEN**

Run: `mvn -q -pl dinghai-shared-state -Dtest=SharedStateCacheConfigurationTest test`
Expected: all configuration tests pass, including coexistence with a business factory.

- [ ] **Step 5: Update README and run the module verification**

Document all new APIs, `CacheValue`, TTL return behavior, batch constraints, and named dedicated factory behavior.
Run: `mvn -q -pl dinghai-shared-state clean verify`
Expected: all unit tests pass; the optional Redis contract test may skip when unavailable.

### Task 6: Completion, Review, and Archive

**Files:**
- Modify: `openspec/changes/add-shared-state-common-operations/tasks.md`
- Modify: `openspec/specs/shared-state-cache/spec.md`
- Create: `openspec/changes/archive/<date>-add-shared-state-common-operations/`

- [ ] **Step 1: Run the full verification matrix**

Run: `mvn -q clean verify && openspec validate add-shared-state-common-operations --strict`
Expected: Maven succeeds; OpenSpec validates; Redis contract reports either pass or its intentional environment skip.

- [ ] **Step 2: Audit implementation against every new requirement**

Check compare-delete, get-set, TTL mapping, TTL-required persist rejection, batch hit/TTL/delete semantics, and dedicated factory identity against source and named tests. Record any discrepancy in the task change before proceeding.

- [ ] **Step 3: Run code review and repair all findings**

Run: `git diff main...HEAD --check` and inspect all changed Java code against `code-review-spec`, `backend-reliability-guidelines`, and `logging-boundaries`. Re-run the full verification matrix after each repair.

- [ ] **Step 4: Sync and archive the verified OpenSpec change**

Copy the approved delta requirements into `openspec/specs/shared-state-cache/spec.md`, archive the change using the repository OpenSpec archive flow, and validate the archived result.

- [ ] **Step 5: Integrate each verified task into `main`**

For every task branch, merge with `--no-ff`, verify `git merge-base --is-ancestor`, remove the linked worktree and branch, then confirm `main` is clean and contains all task commits.
