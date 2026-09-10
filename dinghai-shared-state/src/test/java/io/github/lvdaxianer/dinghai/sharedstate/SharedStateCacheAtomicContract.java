package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 共享状态后端的单进程并发原子性契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public abstract class SharedStateCacheAtomicContract extends SharedStateCacheContract {

    /** 普通缓存命名空间。 */
    private static final String CACHE_NAMESPACE = "contract-cache";

    /** 普通字符串缓存键路径。 */
    private static final CacheKeyNamespace CACHE_VALUE_KEYS =
            new CacheKeyNamespace(CACHE_NAMESPACE, "contract", "atomic-value");

    /** 并发测试线程数。 */
    protected static final int CONCURRENT_WORKERS = 8;

    /** 永久有效策略。 */
    private static final CacheExpiry ETERNAL = CacheExpiry.eternal();

    /** 并发测试辅助器。 */
    protected SharedStateCacheAtomicTestSupport support;

    /**
     * 创建并发测试辅助器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Before
    public void setUpAtomicContract() {
        support = new SharedStateCacheAtomicTestSupport(CONCURRENT_WORKERS);
    }

    /**
     * 关闭并发测试辅助器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @After
    public void tearDownAtomicContract() {
        support.close();
    }

    /**
     * 并发 putIfAbsent 应只允许一个调用方写入。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldPutIfAbsentAtomicallyWithinProcess() throws Exception {
        CacheKey<String> key = stringKey("concurrent-put-if-absent");
        SharedStateCacheAtomicTestSupport.Gate gate = support.newGate();
        List<Callable<Boolean>> tasks = support.indexedTasks(index -> () ->
                support.gatedCall(gate, () -> cache.putIfAbsent(key, "value-" + index, ETERNAL)));
        List<Future<Boolean>> results = support.submitAndRelease(gate, tasks);

        Assert.assertEquals(1L, support.countTrue(results));
        Assert.assertTrue(cache.get(key).isPresent());
    }

    /**
     * 并发 getAndDelete 应只允许一个调用方取得值。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldGetAndDeleteAtomicallyWithinProcess() throws Exception {
        CacheKey<String> key = stringKey("concurrent-get-and-delete");
        cache.put(key, "once", ETERNAL);
        SharedStateCacheAtomicTestSupport.Gate gate = support.newGate();
        List<Callable<Optional<String>>> tasks = support.repeatedTasks(() ->
                support.gatedCall(gate, () -> cache.getAndDelete(key)));
        List<Future<Optional<String>>> results = support.submitAndRelease(gate, tasks);

        Assert.assertEquals(1L, support.countPresent(results));
        Assert.assertFalse(cache.exists(key));
    }

    /**
     * 并发 compareAndSet 应只允许一个调用方完成同一旧值更新。
     *
     * @throws Exception 并发任务执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldCompareAndSetAtomicallyWithinProcess() throws Exception {
        CacheKey<String> key = stringKey("concurrent-compare-and-set");
        cache.put(key, "before", ETERNAL);
        SharedStateCacheAtomicTestSupport.Gate gate = support.newGate();
        List<Callable<Boolean>> tasks = support.indexedTasks(index -> () ->
                support.gatedCall(gate, () -> compareAndSet(key, "after-" + index)));
        List<Future<Boolean>> results = support.submitAndRelease(gate, tasks);

        Assert.assertEquals(1L, support.countTrue(results));
        Assert.assertNotEquals(Optional.of("before"), cache.get(key));
    }

    /**
     * 创建普通字符串缓存键。
     *
     * @param identifier 标识符
     * @return 字符串缓存键
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKey<String> stringKey(String identifier) {
        return keyBuilder.build(CACHE_VALUE_KEYS, identifier, String.class);
    }

    /**
     * 执行比较更新。
     *
     * @param key 缓存键
     * @param newValue 新值
     * @return 是否更新成功
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private boolean compareAndSet(CacheKey<String> key, String newValue) {
        CompareAndSetCommand<String> command =
                new CompareAndSetCommand<>("before", newValue, ETERNAL);
        return cache.compareAndSet(key, command);
    }

}
