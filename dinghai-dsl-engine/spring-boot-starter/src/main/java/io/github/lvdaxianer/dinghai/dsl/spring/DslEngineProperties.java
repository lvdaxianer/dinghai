package io.github.lvdaxianer.dinghai.dsl.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
