package io.github.lvdaxianer.dinghai.sharedstate;

import com.github.benmanes.caffeine.cache.Ticker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.Assert;
import org.junit.Test;

/**
 * Caffeine 共享状态后端契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class CaffeineSharedStateCacheTest extends SharedStateCacheIncrementContract {

    /** 最大缓存条目数。 */
    private static final long MAXIMUM_SIZE = 100L;

    /** 并发回源加载等待秒数。 */
    private static final long LOADER_LATCH_TIMEOUT_SECONDS = 5L;

    /** 读穿加载键路径。 */
    private static final CacheKeyNamespace LOAD_KEYS =
            new CacheKeyNamespace("contract-cache", "contract", "load");

    /** 可控测试时钟。 */
    private final MutableTicker ticker = new MutableTicker();

    /**
     * 创建使用可控时钟的 Caffeine 后端。
     *
     * @return Caffeine 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    protected SharedStateCache createCache() {
        return new CaffeineSharedStateCache(
                MAXIMUM_SIZE, ticker, new JacksonCacheEnvelopeCodec(new ObjectMapper()));
    }

    /**
     * 推进测试时钟。
     *
     * @param duration 推进时长
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    protected void advanceTime(Duration duration) {
        ticker.advance(duration);
    }

    /**
     * 并发 getOrLoad 同一缺失键时应按键合并回源，loader 仅执行一次。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldMergeConcurrentLoadsForSingleMissingKey() throws Exception {
        CacheKey<String> key = keyBuilder.build(LOAD_KEYS, "single-flight", String.class);
        AtomicInteger loaderCalls = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch loaderRelease = new CountDownLatch(1);
        Supplier<String> loader =
                blockingLoader(loaderCalls, loaderEntered, loaderRelease);
        SharedStateCacheAtomicTestSupport.Gate gate = support.newGate();
        List<Callable<String>> tasks = support.repeatedTasks(() -> support.gatedCall(gate,
                () -> cache.getOrLoad(key, CacheExpiry.eternal(), loader)));
        List<Future<String>> results = support.submitAndRelease(gate, tasks);

        // 等待首个回源加载开始，确保其余线程在加载期间竞争同一键。
        Assert.assertTrue(loaderEntered.await(LOADER_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        loaderRelease.countDown();
        support.awaitResults(results);

        // 全部并发调用获得相同值，且回源加载器只执行一次。
        for (Future<String> result : results) {
            Assert.assertEquals("loaded", result.get());
        }
        Assert.assertEquals(1, loaderCalls.get());
    }

    /**
     * 创建可阻塞的回源加载器，用于放大并发竞争窗口。
     *
     * @param loaderCalls 回源调用计数器
     * @param loaderEntered 回源开始门闩
     * @param loaderRelease 回源放行门闩
     * @return 阻塞式回源加载器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private Supplier<String> blockingLoader(AtomicInteger loaderCalls,
            CountDownLatch loaderEntered, CountDownLatch loaderRelease) {
        return () -> {
            loaderCalls.incrementAndGet();
            loaderEntered.countDown();
            try {
                loaderRelease.await(LOADER_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "loaded";
        };
    }

    /**
     * 可手动推进的 Caffeine 测试时钟。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private static final class MutableTicker implements Ticker {

        /** 当前纳秒时间。 */
        private final AtomicLong nanoseconds = new AtomicLong();

        /**
         * 读取当前纳秒时间。
         *
         * @return 当前纳秒时间
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        @Override
        public long read() {
            return nanoseconds.get();
        }

        /**
         * 推进当前时间。
         *
         * @param duration 推进时长
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private void advance(Duration duration) {
            nanoseconds.addAndGet(duration.toNanos());
        }
    }
}
