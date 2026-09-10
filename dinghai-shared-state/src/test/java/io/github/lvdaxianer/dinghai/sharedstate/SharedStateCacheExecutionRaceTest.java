package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;

/**
 * 共享状态恢复、降级和本地镜像的并发时序测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class SharedStateCacheExecutionRaceTest {

    /** 并发步骤等待秒数。 */
    private static final long WAIT_SECONDS = 5L;

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "execution-race";

    /** 普通缓存键路径。 */
    private static final CacheKeyNamespace CACHE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "cache", "race");

    /** 测试时间源。 */
    private AtomicLong now;

    /** Caffeine 本地后端。 */
    private CaffeineSharedStateCache caffeine;

    /** 测试缓存键。 */
    private CacheKey<String> key;

    /** 容错执行协调器。 */
    private SharedStateCacheExecution execution;

    /**
     * 创建阈值为一次失败的测试协调器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    public void setUp() {
        now = new AtomicLong(1_000L);
        caffeine = ResilientTestCaches.newCaffeineBackend();
        key = new CacheKeyBuilder(NamespacePolicyRegistry.of(
                NamespacePolicy.cacheFallback(CACHE_NAMESPACE)))
                .build(CACHE_KEYS, "value", String.class);
        execution = new SharedStateCacheExecution(Optional.of(mock(SharedStateCache.class)),
                caffeine, options());
    }

    /**
     * 降级 loader 在恢复锁外执行，不得阻塞半开恢复；跨代降级写入不得残留。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldNotBlockRecoveryBehindFallbackLoader() throws Exception {
        openCircuit();
        CountDownLatch fallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseFallback = new CountDownLatch(1);
        ExecutorService executor = SharedStateCacheTestExecutor.create(2);
        try {
            Future<String> fallback = executor.submit(() -> holdFallback(
                    fallbackEntered, releaseFallback));
            await(fallbackEntered);
            now.set(2_001L);
            Future<String> probe = executor.submit(this::successfulProbe);
            // 降级 loader 不再持有恢复锁，半开探测应立即完成恢复而不会被阻塞。
            Assert.assertEquals("remote", probe.get(WAIT_SECONDS, TimeUnit.SECONDS));
            Assert.assertEquals(SharedStateCircuitState.CLOSED, execution.health().circuitState());
            releaseFallback.countDown();
            // 跨代降级复核必须清除降级写入，恢复后不得观察到降级值残留。
            Assert.assertEquals("fallback", fallback.get(WAIT_SECONDS, TimeUnit.SECONDS));
            Assert.assertNotEquals(Optional.of("fallback"), caffeine.get(key));
        } finally {
            releaseFallback.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 旧代次 Redis 成功不得覆盖新故障代次的本地降级值。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIgnoreLateSuccessMirrorFromOlderGeneration() throws Exception {
        CountDownLatch oldRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseOldRequest = new CountDownLatch(1);
        ExecutorService executor = SharedStateCacheTestExecutor.create(1);
        try {
            Future<String> oldRequest = executor.submit(() -> executeOldRequest(
                    oldRequestEntered, releaseOldRequest));
            await(oldRequestEntered);
            Assert.assertEquals("fallback", executeFailure("fallback"));
            Assert.assertEquals(Optional.of("fallback"), caffeine.get(key));

            releaseOldRequest.countDown();
            Assert.assertEquals("remote-old", oldRequest.get(WAIT_SECONDS, TimeUnit.SECONDS));
            Assert.assertEquals(Optional.of("fallback"), caffeine.get(key));
        } finally {
            releaseOldRequest.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 打开 Redis 熔断。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void openCircuit() {
        Assert.assertEquals("initial", executeFailure("initial"));
        Assert.assertEquals(SharedStateCircuitState.OPEN, execution.health().circuitState());
    }

    /**
     * 在本地降级操作中等待释放信号后写入降级值。
     *
     * @param entered 已进入降级操作的信号
     * @param release 释放降级操作的信号
     * @return 本地降级结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String holdFallback(CountDownLatch entered, CountDownLatch release) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(backend -> "unused",
                () -> {
                    entered.countDown();
                    await(release);
                    caffeine.put(key, "fallback", CacheExpiry.eternal());
                    return "fallback";
                }, ignored -> Assert.fail("熔断打开时不得执行成功镜像")));
    }

    /**
     * 执行半开成功探测。
     *
     * @return Redis 探测结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private String successfulProbe() {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(backend -> "remote",
                () -> "unexpected", ignored -> caffeine.put(
                        key, "remote", CacheExpiry.eternal())));
    }

    /**
     * 执行延迟返回的旧代次 Redis 请求。
     *
     * @param entered Redis 请求已开始信号
     * @param release Redis 请求释放信号
     * @return Redis 旧请求结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private String executeOldRequest(CountDownLatch entered, CountDownLatch release) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(backend -> {
            entered.countDown();
            await(release);
            return "remote-old";
        }, () -> "unexpected", result -> caffeine.put(
                key, result, CacheExpiry.eternal())));
    }

    /**
     * 执行一次可计入熔断的 Redis 失败。
     *
     * @param fallbackValue 本地降级值
     * @return 本地降级结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private String executeFailure(String fallbackValue) {
        return execution.execute(key, new SharedStateCacheOperationPlan<>(backend -> {
            throw new RedisBackendException(new IllegalStateException("redis down"));
        }, () -> {
            caffeine.put(key, fallbackValue, CacheExpiry.eternal());
            return fallbackValue;
        }, ignored -> Assert.fail("Redis 失败时不得执行成功镜像")));
    }

    /**
     * 等待并发步骤信号并在超时时失败。
     *
     * @param latch 待等待信号
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private void await(CountDownLatch latch) {
        try {
            Assert.assertTrue("并发步骤等待超时", latch.await(WAIT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发步骤被中断", exception);
        }
    }

    /**
     * 创建测试容错选项。
     *
     * @return 测试容错选项
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private ResilientSharedStateCacheOptions options() {
        SharedStateCacheProperties.CircuitProperties properties =
                new SharedStateCacheProperties.CircuitProperties();
        properties.setFailureThreshold(1);
        properties.setOpenDuration(Duration.ofSeconds(1L));
        return new ResilientSharedStateCacheOptions(properties, now::get);
    }
}
