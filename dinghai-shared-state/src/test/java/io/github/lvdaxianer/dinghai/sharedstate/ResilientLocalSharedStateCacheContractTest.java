package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

/**
 * 无 Redis 模式下容错门面的完整共享缓存契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class ResilientLocalSharedStateCacheContractTest
        extends SharedStateCacheIncrementContract {

    /** 最大本地条目数。 */
    private static final long MAXIMUM_SIZE = 100L;

    /** 可控 Caffeine 时间源。 */
    private final MutableTicker ticker = new MutableTicker();

    /**
     * 创建无 Redis 的容错缓存门面。
     *
     * @return 容错缓存门面
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    protected SharedStateCache createCache() {
        CaffeineSharedStateCache caffeine = new CaffeineSharedStateCache(
                MAXIMUM_SIZE, ticker,
                new JacksonCacheEnvelopeCodec(new ObjectMapper()));
        return new ResilientSharedStateCache(Optional.empty(), caffeine,
                new ResilientSharedStateCacheOptions(
                        new SharedStateCacheProperties.CircuitProperties(),
                        System::currentTimeMillis));
    }

    /**
     * 推进 Caffeine 测试时间。
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
     * 单机模式不应累计普通 fallback，但应标记临时状态一致性损失。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldExposeLocalOnlyConsistencyState() {
        ResilientSharedStateCache resilient = (ResilientSharedStateCache) cache;
        CacheKey<String> stateKey = keyBuilder.build(
                new CacheKeyNamespace("contract-state", "auth", "local-only"),
                "identifier", String.class);

        Assert.assertEquals(Optional.empty(), resilient.get(stateKey));

        Assert.assertEquals(0L, resilient.metrics().fallbackCount());
        Assert.assertEquals(1L, resilient.metrics().consistencyLossCount());
        Assert.assertTrue(resilient.health().isDegraded());
    }

    /**
     * 可手动推进的 Caffeine 时间源。
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
         * 推进当前纳秒时间。
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
