package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/**
 * 共享状态契约值对象测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class SharedStateContractModelTest {

    /** 后端中立契约要求的操作名称。 */
    private static final Set<String> REQUIRED_OPERATIONS = Set.of(
            "get", "put", "putIfAbsent", "getOrLoad", "getAndDelete", "increment",
            "compareAndSet", "compareAndDelete", "getAndSet", "getAndExpire", "remainingTtl",
            "expire", "persist", "getAll", "putAll", "deleteAll", "delete", "exists");

    /**
     * 后端中立接口应完整暴露约定操作。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldExposeRequiredBackendNeutralOperations() {
        // 执行：反射收集后端中立接口声明的全部方法名。
        Set<String> operationNames = Arrays.stream(SharedStateCache.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        // 断言：方法名集合与契约要求的操作完全一致，不多不少。
        Assert.assertEquals(REQUIRED_OPERATIONS, operationNames);
    }

    /**
     * 类型化信封应保留版本、声明类型和值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRetainTypedEnvelopeMetadata() {
        // 准备：构造 String 类型的类型化信封。
        CacheEnvelope<String> envelope = CacheEnvelope.of(String.class, "green");

        // 断言：信封保留当前模式版本、声明类型标识与原始值。
        Assert.assertEquals(CacheEnvelope.CURRENT_SCHEMA_VERSION, envelope.schemaVersion());
        Assert.assertEquals(String.class.getName(), envelope.typeIdentifier());
        Assert.assertEquals("green", envelope.value());
    }

    /**
     * 到期策略应区分永久条目和正数 TTL 条目。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldRepresentEternalAndPositiveExpiry() {
        // 准备：分别构造永久有效与 30 秒 TTL 的到期策略。
        CacheExpiry eternal = CacheExpiry.eternal();
        CacheExpiry expiring = CacheExpiry.after(Duration.ofSeconds(30L));

        // 断言：永久策略标记为永久有效，TTL 策略保留给定有效时长。
        Assert.assertTrue(eternal.isEternal());
        Assert.assertFalse(expiring.isEternal());
        Assert.assertEquals(Duration.ofSeconds(30L), expiring.ttl());
    }

    /**
     * 非正数 TTL 应在进入后端前被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveExpiry() {
        // 执行与断言：零 TTL 在进入后端前被立即拒绝，抛出非法参数异常。
        CacheExpiry.after(Duration.ZERO);
    }
}
