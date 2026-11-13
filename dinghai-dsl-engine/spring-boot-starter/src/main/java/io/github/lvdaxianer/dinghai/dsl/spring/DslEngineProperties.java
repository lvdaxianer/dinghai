package io.github.lvdaxianer.dinghai.dsl.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DSL 引擎公共启动配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
@ConfigurationProperties("dinghai.data-query")
public class DslEngineProperties {

    private String defaultDataAdapter = "mysql";

    private Map<String, String> dslAdapters = new LinkedHashMap<>(Map.of("relational-query", "enabled"));

    private int maxRequestBytes = 1_048_576;

    private int maxConcurrency = 32;

    private Duration timeout = Duration.ofSeconds(30);

    private int maxPlanNodes = 1_000;

    private int maxPlanDepth = 16;

    private int maxJoins = 8;

    private int maxRows = 10_000;

    private int maxParameters = 256;

    private int maxInItems = 1_000;

    /**
     * 返回默认数据适配器类型。
     *
     * @return 默认后端类型
     */
    public String getDefaultDataAdapter() {
        return defaultDataAdapter;
    }

    /**
     * 设置默认数据适配器类型。
     *
     * @param defaultDataAdapter 默认后端类型
     */
    public void setDefaultDataAdapter(String defaultDataAdapter) {
        this.defaultDataAdapter = defaultDataAdapter;
    }

    /**
     * 返回 DSL 适配器启用配置。
     *
     * @return DSL 类型到 enabled/disabled 的映射
     */
    public Map<String, String> getDslAdapters() {
        return dslAdapters;
    }

    /**
     * 设置 DSL 适配器启用配置。
     *
     * @param dslAdapters DSL 类型到 enabled/disabled 的映射
     */
    public void setDslAdapters(Map<String, String> dslAdapters) {
        this.dslAdapters = dslAdapters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dslAdapters);
    }

    /**
     * 返回请求大小上限。
     *
     * @return 请求字节上限
     */
    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    /**
     * 设置请求大小上限。
     *
     * @param maxRequestBytes 请求字节上限
     */
    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    /**
     * 返回最大并发数。
     *
     * @return 并发上限
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * 设置最大并发数。
     *
     * @param maxConcurrency 并发上限
     */
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    /**
     * 返回执行超时。
     *
     * @return 执行超时
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 设置执行超时。
     *
     * @param timeout 执行超时
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * 返回计划节点上限。
     *
     * @return 节点上限
     */
    public int getMaxPlanNodes() {
        return maxPlanNodes;
    }

    /**
     * 设置计划节点上限。
     *
     * @param maxPlanNodes 节点上限
     */
    public void setMaxPlanNodes(int maxPlanNodes) {
        this.maxPlanNodes = maxPlanNodes;
    }

    /**
     * 返回计划深度上限。
     *
     * @return 深度上限
     */
    public int getMaxPlanDepth() {
        return maxPlanDepth;
    }

    /**
     * 设置计划深度上限。
     *
     * @param maxPlanDepth 深度上限
     */
    public void setMaxPlanDepth(int maxPlanDepth) {
        this.maxPlanDepth = maxPlanDepth;
    }

    /**
     * 返回 JOIN 上限。
     *
     * @return JOIN 上限
     */
    public int getMaxJoins() {
        return maxJoins;
    }

    /**
     * 设置 JOIN 上限。
     *
     * @param maxJoins JOIN 上限
     */
    public void setMaxJoins(int maxJoins) {
        this.maxJoins = maxJoins;
    }

    /**
     * 返回最大返回行数。
     *
     * @return 最大返回行数
     */
    public int getMaxRows() {
        return maxRows;
    }

    /**
     * 设置最大返回行数。
     *
     * @param maxRows 最大返回行数
     */
    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    /**
     * 返回单次查询允许引用的最大参数数量。
     *
     * @return 参数数量上限
     */
    public int getMaxParameters() {
        return maxParameters;
    }

    /**
     * 设置单次查询允许引用的最大参数数量。
     *
     * @param maxParameters 参数数量上限
     */
    public void setMaxParameters(int maxParameters) {
        this.maxParameters = maxParameters;
    }

    /**
     * 返回单个 IN 参数允许的最大元素数量。
     *
     * @return IN 元素上限
     */
    public int getMaxInItems() {
        return maxInItems;
    }

    /**
     * 设置单个 IN 参数允许的最大元素数量。
     *
     * @param maxInItems IN 元素上限
     */
    public void setMaxInItems(int maxInItems) {
        this.maxInItems = maxInItems;
    }

    /**
     * 返回 JDBC 使用的秒级超时。
     *
     * @return 至少一秒的 JDBC 超时
     */
    public int getQueryTimeoutSeconds() {
        long seconds = timeout == null ? 0 : timeout.toSeconds();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, seconds));
    }
}
