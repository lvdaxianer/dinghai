package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
 * Redis 连接属性测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-08-01
 */
public class RedisConnectionPropertiesTest {

    /**
     * 未设置模式时应保持单机默认行为。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test
    public void shouldDefaultToStandaloneMode() {
        RedisConnectionProperties properties = new RedisConnectionProperties();

        Assert.assertEquals(RedisConnectionProperties.RedisMode.STANDALONE,
                properties.getMode());
        Assert.assertFalse(properties.isConfigured());
    }

    /**
     * 有效 Sentinel 配置应被识别并规范化节点列表。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test
    public void shouldRecognizeValidSentinelConfiguration() {
        RedisConnectionProperties properties = sentinelProperties();

        Assert.assertTrue(properties.isConfigured());
        properties.validate();
        Assert.assertEquals(2, properties.getSentinel().getNodes().size());
    }

    /**
     * 未知 Redis 模式应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnknownRedisMode() {
        RedisConnectionProperties.RedisMode.parse("cluster");
    }

    /**
     * Sentinel master 缺失时应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSentinelWithoutMaster() {
        RedisConnectionProperties properties = new RedisConnectionProperties();
        properties.setMode(RedisConnectionProperties.RedisMode.SENTINEL);
        properties.getSentinel().setNodes(Arrays.asList("127.0.0.1:26379"));

        properties.validate();
    }

    /**
     * Sentinel 节点缺失时应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSentinelWithoutNodes() {
        RedisConnectionProperties properties = new RedisConnectionProperties();
        properties.setMode(RedisConnectionProperties.RedisMode.SENTINEL);
        properties.getSentinel().setMaster("mymaster");

        properties.validate();
    }

    /**
     * Sentinel 节点端口非法时应被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidSentinelPort() {
        RedisConnectionProperties properties = new RedisConnectionProperties();
        properties.setMode(RedisConnectionProperties.RedisMode.SENTINEL);
        properties.getSentinel().setMaster("mymaster");
        properties.getSentinel().setNodes(Arrays.asList("127.0.0.1:70000"));

        properties.validate();
    }

    /**
     * 构造有效 Sentinel 属性。
     *
     * @return Sentinel Redis 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisConnectionProperties sentinelProperties() {
        RedisConnectionProperties properties = new RedisConnectionProperties();
        properties.setMode(RedisConnectionProperties.RedisMode.SENTINEL);
        properties.setDatabase(3);
        properties.setPassword("redis-secret");
        properties.getSentinel().setMaster("mymaster");
        properties.getSentinel().setNodes(Arrays.asList(
                "127.0.0.1:26379, 127.0.0.1:26380"));
        properties.getSentinel().setUsername("sentinel-user");
        properties.getSentinel().setPassword("sentinel-secret");
        return properties;
    }
}
