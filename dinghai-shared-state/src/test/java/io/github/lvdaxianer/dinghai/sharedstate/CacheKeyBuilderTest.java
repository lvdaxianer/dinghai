package io.github.lvdaxianer.dinghai.sharedstate;

import java.lang.reflect.Modifier;
import org.junit.Assert;
import org.junit.Test;

/**
 * Redis 风格缓存键构建器测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public class CacheKeyBuilderTest {

    /**
     * 普通缓存命名空间应保留统一格式的可读标识符。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldBuildPlainKeyForOrdinaryNamespace() {
        NamespacePolicy policy = NamespacePolicy.cacheFallback("profile");
        NamespacePolicyRegistry registry = NamespacePolicyRegistry.of(policy);
        CacheKeyBuilder builder = new CacheKeyBuilder(registry);
        CacheKeyNamespace keyNamespace = new CacheKeyNamespace("profile", "user", "profile");

        CacheKey<String> plainKey = builder.build(keyNamespace, "alice", String.class);

        Assert.assertEquals("dinghai:user:profile:alice", plainKey.value());
    }

    /**
     * 缓存键构建器应拒绝空白标识符。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBlankCacheKeyValue() {
        profileKeyBuilder().build(profileKeyNamespace(), " ", String.class);
    }

    /**
     * 强制 TTL 的命名空间应自动哈希敏感标识符。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldHashIdentifierWhenNamespaceRequiresTtl() {
        NamespacePolicy policy = NamespacePolicy.stateFallback("oauth-state");
        CacheKeyBuilder builder = new CacheKeyBuilder(NamespacePolicyRegistry.of(policy));
        CacheKeyNamespace namespace = new CacheKeyNamespace("oauth-state", "oauth", "state");

        CacheKey<String> key = builder.build(namespace, "alice", String.class);

        Assert.assertEquals(
                "dinghai:oauth:state:2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90",
                key.value());
        Assert.assertFalse(key.value().contains("alice"));
    }

    /**
     * 缓存键应只能由构建器通过非公开实现创建。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test
    public void shouldExposeSealedBuilderOnlyKeyContract() {
        Assert.assertTrue(CacheKey.class.isInterface());
        Assert.assertTrue(CacheKey.class.isSealed());

        Class<?>[] permittedTypes = CacheKey.class.getPermittedSubclasses();
        Assert.assertEquals(1, permittedTypes.length);
        Assert.assertFalse(Modifier.isPublic(permittedTypes[0].getModifiers()));
    }

    /**
     * 未注册命名空间应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnregisteredNamespace() {
        CacheKeyNamespace unknown = new CacheKeyNamespace("unknown", "user", "profile");
        profileKeyBuilder().build(unknown, "alice", String.class);
    }

    /**
     * 包含分隔符的模块片段应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidModuleSegment() {
        new CacheKeyNamespace("profile", "user:admin", "profile");
    }

    /**
     * 包含分隔符的标识符片段应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidIdentifierSegment() {
        profileKeyBuilder().build(profileKeyNamespace(), "alice:admin", String.class);
    }

    /**
     * 超长标识符片段应在哈希替换前被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectOverlongIdentifierSegment() {
        profileKeyBuilder().build(profileKeyNamespace(), "a".repeat(129), String.class);
    }

    /**
     * 超长模块片段应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectOverlongModuleSegment() {
        new CacheKeyNamespace("profile", "m".repeat(129), "profile");
    }

    /**
     * 达到片段长度上限的键应正常构建。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldAcceptSegmentAtMaximumLength() {
        String identifier = "a".repeat(128);

        CacheKey<String> key = profileKeyBuilder()
                .build(profileKeyNamespace(), identifier, String.class);

        Assert.assertEquals("dinghai:user:profile:" + identifier, key.value());
    }

    /**
     * 强制 TTL 命名空间的超长原始标识符应在哈希前被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectOverlongIdentifierBeforeHashing() {
        NamespacePolicy policy = NamespacePolicy.stateFallback("oauth-state");
        CacheKeyBuilder builder = new CacheKeyBuilder(NamespacePolicyRegistry.of(policy));
        CacheKeyNamespace namespace = new CacheKeyNamespace("oauth-state", "oauth", "state");

        builder.build(namespace, "s".repeat(129), String.class);
    }

    /**
     * 创建普通缓存键构建器。
     *
     * @return 缓存键构建器
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKeyBuilder profileKeyBuilder() {
        NamespacePolicy policy = NamespacePolicy.cacheFallback("profile");
        return new CacheKeyBuilder(NamespacePolicyRegistry.of(policy));
    }

    /**
     * 创建普通缓存键路径。
     *
     * @return 缓存键路径
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheKeyNamespace profileKeyNamespace() {
        return new CacheKeyNamespace("profile", "user", "profile");
    }
}
