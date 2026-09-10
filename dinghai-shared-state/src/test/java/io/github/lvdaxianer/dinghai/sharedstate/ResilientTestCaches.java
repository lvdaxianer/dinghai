package io.github.lvdaxianer.dinghai.sharedstate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 容错测试共享的 Caffeine 后端夹具。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class ResilientTestCaches {

    /** 测试后端最大容量。 */
    private static final long MAXIMUM_SIZE = 100L;

    /**
     * 禁止实例化测试夹具。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private ResilientTestCaches() {
    }

    /**
     * 创建带 Jackson 编解码器的测试 Caffeine 后端。
     *
     * @return 测试 Caffeine 后端
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    static CaffeineSharedStateCache newCaffeineBackend() {
        return new CaffeineSharedStateCache(MAXIMUM_SIZE,
                new JacksonCacheEnvelopeCodec(new ObjectMapper()));
    }
}
