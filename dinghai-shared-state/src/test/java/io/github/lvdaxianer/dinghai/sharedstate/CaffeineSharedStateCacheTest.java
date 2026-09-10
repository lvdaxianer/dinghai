package io.github.lvdaxianer.dinghai.sharedstate;

import com.github.benmanes.caffeine.cache.Ticker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caffeine 共享状态后端契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class CaffeineSharedStateCacheTest extends SharedStateCacheIncrementContract {

    /** 最大缓存条目数。 */
    private static final long MAXIMUM_SIZE = 100L;

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
