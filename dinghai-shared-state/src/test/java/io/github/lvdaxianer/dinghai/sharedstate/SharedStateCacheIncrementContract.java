package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Test;

/**
 * 共享状态后端的单进程递增原子性契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public abstract class SharedStateCacheIncrementContract extends SharedStateCacheAtomicContract {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "contract-cache";

    /** 普通计数器缓存键路径。 */
    private static final CacheKeyNamespace CACHE_COUNTER_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "contract", "atomic-counter");

    /** 每个线程执行的递增次数。 */
    private static final int INCREMENTS_PER_WORKER = 100;

    /** 永久有效策略。 */
    private static final CacheExpiry ETERNAL = CacheExpiry.eternal();

    /**
     * 并发 increment 应保持单进程原子结果。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldIncrementAtomicallyWithinProcess() throws Exception {
        CacheKey<Long> key = longKey("concurrent-increment");
        List<Future<Long>> results = support.invokeAll(
                support.repeatedTasks(() -> incrementRepeatedly(key)));
        support.awaitResults(results);
        long expected = (long) CONCURRENT_WORKERS * INCREMENTS_PER_WORKER;
        Assert.assertEquals(Optional.of(expected), cache.get(key));
    }

    /**
     * 创建普通长整型缓存键。
     *
     * @param identifier 标识符
     * @return 长整型缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<Long> longKey(String identifier) {
        return keyBuilder.build(CACHE_COUNTER_KEYS, identifier, Long.class);
    }

    /**
     * 重复递增缓存值。
     *
     * @param key 长整型缓存键
     * @return 最后一次递增结果
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private long incrementRepeatedly(CacheKey<Long> key) {
        long result = 0L;
        for (int count = 0; count < INCREMENTS_PER_WORKER; count++) {
            result = cache.increment(key, 1L, ETERNAL);
        }
        return result;
    }
}
