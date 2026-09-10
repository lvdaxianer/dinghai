package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;

/**
 * 已解析的 Redis Sentinel 节点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-08-01
 */
final class RedisSentinelNode {

    /** 最大端口号。 */
    static final int MAX_PORT = 65_535;

    /** 节点主机。 */
    private final String host;

    /** 节点端口。 */
    private final int port;

    /**
     * 创建 Sentinel 节点。
     *
     * @param host 节点主机
     * @param port 节点端口
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private RedisSentinelNode(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 解析 `host:port` 节点文本。
     *
     * @param value 节点文本
     * @return 已解析节点
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    static RedisSentinelNode parse(String value) {
        String endpoint = Objects.requireNonNull(value, "sentinel node").trim();
        int separator = endpoint.lastIndexOf(':');
        // 分隔符缺失、位于开头（缺主机）或位于末尾（缺端口）都不是合法节点。
        if (separator <= 0 || separator == endpoint.length() - 1) {
            throw invalidNode(endpoint, null);
        }
        String host = endpoint.substring(0, separator).trim();
        int port = parsePort(endpoint, endpoint.substring(separator + 1).trim());
        // 主机去除空白后为空时拒绝，避免生成无法路由的节点。
        if (host.isEmpty()) {
            throw invalidNode(endpoint, null);
        }
        return new RedisSentinelNode(host, port);
    }

    /**
     * 获取节点主机。
     *
     * @return 节点主机
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    String host() {
        return host;
    }

    /**
     * 获取节点端口。
     *
     * @return 节点端口
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    int port() {
        return port;
    }

    /**
     * 解析并校验节点端口。
     *
     * @param endpoint 原始节点文本
     * @param portText 端口文本
     * @return 节点端口
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static int parsePort(String endpoint, String portText) {
        try {
            int port = Integer.parseInt(portText);
            // 端口必须落在 1~65535 的合法 TCP 端口范围内。
            if (port <= 0 || port > MAX_PORT) {
                throw invalidNode(endpoint, null);
            }
            return port;
        } catch (NumberFormatException exception) {
            throw invalidNode(endpoint, exception);
        }
    }

    /**
     * 创建统一的节点配置异常。
     *
     * @param endpoint 原始节点文本
     * @param cause 原始异常
     * @return 配置异常
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private static IllegalArgumentException invalidNode(String endpoint, Throwable cause) {
        String message = "dinghai.cache.redis.sentinel.nodes contains invalid node: " + endpoint;
        return cause == null ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}
