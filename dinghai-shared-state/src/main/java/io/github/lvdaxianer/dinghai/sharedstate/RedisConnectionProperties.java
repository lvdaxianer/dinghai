package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Locale;
import java.util.Objects;

/**
 * Redis 连接模式和连接参数。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-08-01
 */
public class RedisConnectionProperties {

    /** 默认 Redis 端口。 */
    private static final int DEFAULT_PORT = 6379;

    /** Redis 连接模式。 */
    private RedisMode mode = RedisMode.STANDALONE;

    /** Redis 主机。 */
    private String host;

    /** Redis 端口。 */
    private int port = DEFAULT_PORT;

    /** Redis 数据库编号。 */
    private int database;

    /** Redis 密码。 */
    private String password;

    /** Sentinel 连接配置。 */
    private final SentinelProperties sentinel = new SentinelProperties();

    /**
     * Redis 连接模式。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public enum RedisMode {
        /** 单机 Redis。 */
        STANDALONE,
        /** Redis Sentinel。 */
        SENTINEL;

        /**
         * 解析配置中的模式值。
         *
         * @param value 模式文本
         * @return Redis 连接模式
         * @author lvdaxianer@yeah.net
         * @date 2026-08-01
         */
        public static RedisMode parse(String value) {
            String normalized = Objects.requireNonNull(value, "mode").trim();
            try {
                return valueOf(normalized.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "dinghai.cache.redis.mode must be standalone or sentinel", exception);
            }
        }
    }

    /**
     * 判断 Redis 配置是否足以启用对应后端。
     *
     * @return true 表示已配置 Redis
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public boolean isConfigured() {
        // Sentinel 模式下以 Sentinel 配置完整性判断是否启用 Redis。
        if (mode == RedisMode.SENTINEL) {
            return sentinel.isConfigured();
        }
        // 单机模式下主机非空白即视为已配置。
        return hasText(host);
    }

    /**
     * 校验启用 Redis 所需的连接配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void validate() {
        // Sentinel 模式校验 Sentinel 专有配置。
        if (mode == RedisMode.SENTINEL) {
            sentinel.validate();
        } else {
            // 单机模式只要求主机配置。
            requireHost();
        }
    }

    /**
     * 获取 Redis 连接模式。
     *
     * @return Redis 连接模式
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public RedisMode getMode() {
        return mode;
    }

    /**
     * 设置 Redis 连接模式。
     *
     * @param mode Redis 连接模式
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setMode(RedisMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * 获取 Redis 主机。
     *
     * @return Redis 主机
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置 Redis 主机。
     *
     * @param host Redis 主机
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 获取 Redis 端口。
     *
     * @return Redis 端口
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置 Redis 端口。
     *
     * @param port Redis 端口
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setPort(int port) {
        // 端口必须落在 1~65535 的合法 TCP 端口范围内。
        if (port <= 0 || port > RedisSentinelNode.MAX_PORT) {
            throw new IllegalArgumentException("dinghai.cache.redis.port must be between 1 and 65535");
        }
        this.port = port;
    }

    /**
     * 获取 Redis 数据库编号。
     *
     * @return Redis 数据库编号
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public int getDatabase() {
        return database;
    }

    /**
     * 设置 Redis 数据库编号。
     *
     * @param database Redis 数据库编号
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setDatabase(int database) {
        // Redis 数据库编号不允许为负数。
        if (database < 0) {
            throw new IllegalArgumentException("dinghai.cache.redis.database must not be negative");
        }
        this.database = database;
    }

    /**
     * 获取 Redis 密码。
     *
     * @return Redis 密码
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置 Redis 密码。
     *
     * @param password Redis 密码
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取 Sentinel 配置。
     *
     * @return Sentinel 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public SentinelProperties getSentinel() {
        return sentinel;
    }

    /**
     * 判断文本是否有内容。
     *
     * @param value 待判断文本
     * @return true 表示文本非空白
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 校验单机 Redis 主机。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private void requireHost() {
        // 单机 Redis 主机为空白时直接拒绝启动。
        if (!hasText(host)) {
            throw new IllegalArgumentException("dinghai.cache.redis.host must not be blank");
        }
    }

}
