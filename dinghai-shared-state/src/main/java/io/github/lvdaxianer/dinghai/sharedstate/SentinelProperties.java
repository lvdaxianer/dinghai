package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis Sentinel 连接配置属性。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-08-01
 */
public class SentinelProperties {

    /** 默认 Sentinel 节点列表容量。 */
    private static final int DEFAULT_NODE_CAPACITY = 1;

    /** Sentinel master 名称。 */
    private String master;

    /** Sentinel 节点列表。 */
    private List<String> nodes = new ArrayList<>(DEFAULT_NODE_CAPACITY);

    /** Sentinel ACL 用户名。 */
    private String username;

    /** Sentinel ACL 密码。 */
    private String password;

    /**
     * 判断 Sentinel 必要配置是否存在。
     *
     * @return true 表示 master 和节点均已配置
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public boolean isConfigured() {
        return master != null && !master.isBlank() && !nodes.isEmpty();
    }

    /**
     * 校验 Sentinel 配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void validate() {
        requireMaster();
        // Sentinel 节点列表为空时无法发现 master，直接拒绝。
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "dinghai.cache.redis.sentinel.nodes must not be empty");
        }
        for (String node : nodes) {
            RedisSentinelNode.parse(node);
        }
    }

    /**
     * 获取 Sentinel master 名称。
     *
     * @return Sentinel master 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public String getMaster() {
        return master;
    }

    /**
     * 设置 Sentinel master 名称。
     *
     * @param master Sentinel master 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setMaster(String master) {
        this.master = master;
    }

    /**
     * 获取 Sentinel 节点列表。
     *
     * @return Sentinel 节点列表
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public List<String> getNodes() {
        return nodes;
    }

    /**
     * 设置 Sentinel 节点列表。
     *
     * @param nodes Sentinel 节点列表
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setNodes(List<String> nodes) {
        List<String> normalizedNodes = new ArrayList<>(
                Math.max(nodes == null ? 0 : nodes.size(), DEFAULT_NODE_CAPACITY));
        if (nodes == null) {
            // null 输入规范化为空列表，避免后续遍历空指针。
            this.nodes = normalizedNodes;
            return;
        }
        for (String node : nodes) {
            addCommaSeparatedNodes(normalizedNodes, node);
        }
        this.nodes = normalizedNodes;
    }

    /**
     * 获取 Sentinel ACL 用户名。
     *
     * @return Sentinel ACL 用户名
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置 Sentinel ACL 用户名。
     *
     * @param username Sentinel ACL 用户名
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取 Sentinel ACL 密码。
     *
     * @return Sentinel ACL 密码
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置 Sentinel ACL 密码。
     *
     * @param password Sentinel ACL 密码
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 添加逗号分隔的 Sentinel 节点。
     *
     * @param target 规范化后的节点列表
     * @param value 节点文本
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private void addCommaSeparatedNodes(List<String> target, String value) {
        if (value == null) {
            // 节点列表不允许包含 null 元素。
            throw new IllegalArgumentException("dinghai.cache.redis.sentinel.nodes contains null");
        }
        for (String node : value.split(",")) {
            // 跳过逗号分隔产生的空白片段，只保留有效节点。
            if (!node.isBlank()) {
                target.add(node.trim());
            }
        }
    }

    /**
     * 校验 Sentinel master 名称。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-08-01
     */
    private void requireMaster() {
        // Sentinel master 名称为空白时无法定位主节点。
        if (master == null || master.isBlank()) {
            throw new IllegalArgumentException(
                    "dinghai.cache.redis.sentinel.master must not be blank");
        }
    }
}
