package io.github.lvdaxianer.dinghai.sharedstate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis 真实契约测试的连接工厂和可达性辅助类。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-08-01
 */
final class RedisContractConnectionFactory {

    /** 单机契约模式。 */
    private static final String STANDALONE_MODE = "standalone";

    /** Sentinel 契约模式。 */
    private static final String SENTINEL_MODE = "sentinel";

    /** Redis 测试主机。 */
    private static final String REDIS_HOST =
            System.getProperty("dinghai.redis.contract-host", "127.0.0.1");

    /** Redis 测试端口。 */
    private static final int REDIS_PORT = Integer.getInteger("dinghai.redis.contract-port", 6379);

    /** Redis 服务端密码。 */
    private static final String REDIS_PASSWORD =
            System.getProperty("dinghai.redis.contract-password", "");

    /** Redis 契约连接模式。 */
    private static final String REDIS_MODE =
            System.getProperty("dinghai.redis.contract-mode", STANDALONE_MODE);

    /** Sentinel master 名称。 */
    private static final String SENTINEL_MASTER =
            System.getProperty("dinghai.redis.contract-sentinel-master", "mymaster");

    /** Sentinel 节点列表。 */
    private static final String SENTINEL_NODES =
            System.getProperty("dinghai.redis.contract-sentinel-nodes", "127.0.0.1:26379");

    /** Sentinel ACL 用户名。 */
    private static final String SENTINEL_USERNAME =
            System.getProperty("dinghai.redis.contract-sentinel-username", "");

    /** Sentinel ACL 密码。 */
    private static final String SENTINEL_PASSWORD =
            System.getProperty("dinghai.redis.contract-sentinel-password", "");

    /** Redis 连接超时时间。 */
    private static final int CONNECT_TIMEOUT_MILLIS = 100;

    /**
     * 禁止实例化静态测试辅助类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisContractConnectionFactory() {
    }

    /**
     * 根据契约模式创建 Redis 连接工厂。
     *
     * @return Redis 连接工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    static LettuceConnectionFactory create() {
        if (SENTINEL_MODE.equalsIgnoreCase(REDIS_MODE)) {
            // Sentinel 模式通过 master 和节点列表发现 Redis 主节点。
            return createSentinel();
        }
        if (STANDALONE_MODE.equalsIgnoreCase(REDIS_MODE)) {
            // 单机模式保留原有 host 和 port 契约配置。
            return createStandalone();
        }
        throw new IllegalArgumentException("unsupported Redis contract mode: " + REDIS_MODE);
    }

    /**
     * 判断契约目标是否可达。
     *
     * @return true 表示契约目标可达
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    static boolean isReachable() {
        if (SENTINEL_MODE.equalsIgnoreCase(REDIS_MODE)) {
            // Sentinel 模式只需先确认至少一个 Sentinel 节点可达。
            return isSentinelReachable();
        }
        // 单机模式直接检查 Redis 服务端点。
        return isEndpointReachable(REDIS_HOST, REDIS_PORT);
    }

    /**
     * 创建单机契约连接工厂。
     *
     * @return 单机 Redis 连接工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static LettuceConnectionFactory createStandalone() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS_HOST, REDIS_PORT);
        // 未配置密码时保留匿名 Redis 连接。
        if (!REDIS_PASSWORD.isBlank()) {
            configuration.setPassword(RedisPassword.of(REDIS_PASSWORD));
        }
        return new LettuceConnectionFactory(configuration);
    }

    /**
     * 创建 Sentinel 契约连接工厂。
     *
     * @return Sentinel Redis 连接工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static LettuceConnectionFactory createSentinel() {
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration()
                .master(SENTINEL_MASTER);
        for (String node : sentinelNodes()) {
            RedisSentinelNode endpoint = RedisSentinelNode.parse(node);
            configuration.sentinel(endpoint.host(), endpoint.port());
        }
        applyRedisPassword(configuration);
        applySentinelCredentials(configuration);
        return new LettuceConnectionFactory(configuration);
    }

    /**
     * 设置 Redis 服务端密码。
     *
     * @param configuration Sentinel 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static void applyRedisPassword(RedisSentinelConfiguration configuration) {
        // Redis 密码独立于 Sentinel ACL 密码，可按需设置。
        if (!REDIS_PASSWORD.isBlank()) {
            configuration.setPassword(RedisPassword.of(REDIS_PASSWORD));
        }
    }

    /**
     * 设置 Sentinel ACL 凭据。
     *
     * @param configuration Sentinel 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static void applySentinelCredentials(RedisSentinelConfiguration configuration) {
        // Sentinel 用户名为空时不向客户端传递用户名。
        if (!SENTINEL_USERNAME.isBlank()) {
            configuration.setSentinelUsername(SENTINEL_USERNAME);
        }
        // Sentinel 密码为空时不向客户端传递密码。
        if (!SENTINEL_PASSWORD.isBlank()) {
            configuration.setSentinelPassword(RedisPassword.of(SENTINEL_PASSWORD));
        }
    }

    /**
     * 获取规范化的 Sentinel 节点列表。
     *
     * @return Sentinel 节点列表
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static List<String> sentinelNodes() {
        return Arrays.stream(SENTINEL_NODES.split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .toList();
    }

    /**
     * 判断第一个 Sentinel 节点是否可连接。
     *
     * @return true 表示 Sentinel 节点可连接
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static boolean isSentinelReachable() {
        List<String> nodes = sentinelNodes();
        // 没有 Sentinel 节点时目标必然不可达。
        if (nodes.isEmpty()) {
            return false;
        }
        RedisSentinelNode endpoint = RedisSentinelNode.parse(nodes.get(0));
        return isEndpointReachable(endpoint.host(), endpoint.port());
    }

    /**
     * 判断 TCP 服务端点是否可达。
     *
     * @param host 服务主机
     * @param port 服务端口
     * @return true 表示服务端点可达
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static boolean isEndpointReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
