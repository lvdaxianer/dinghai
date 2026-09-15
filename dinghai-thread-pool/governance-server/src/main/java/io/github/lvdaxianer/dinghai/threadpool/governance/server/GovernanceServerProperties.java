package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 单实例总控的本地控制面配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
@ConfigurationProperties("dinghai.thread-pool.governance")
public class GovernanceServerProperties {
    private Path sqlitePath = Path.of("dinghai-governance.db");
    private Duration pollInterval = Duration.ofSeconds(30);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration retention = Duration.ofDays(30);
    private int pageLimit = 1000;
    private String apiToken;

    /**
     * 返回总控 SQLite 路径。
     *
     * @return SQLite 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Path getSqlitePath() { return sqlitePath; }

    /**
     * 设置总控 SQLite 路径。
     *
     * @param sqlitePath SQLite 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setSqlitePath(Path sqlitePath) { this.sqlitePath = sqlitePath; }

    /**
     * 返回连续轮询间隔。
     *
     * @return 轮询间隔
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Duration getPollInterval() { return pollInterval; }

    /**
     * 设置连续轮询间隔。
     *
     * @param pollInterval 轮询间隔
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }

    /**
     * 返回每个子控请求的连接与读取超时。
     *
     * @return 请求超时
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Duration getRequestTimeout() { return requestTimeout; }

    /**
     * 设置每个子控请求的连接与读取超时。
     *
     * @param requestTimeout 请求超时
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    /**
     * 返回总控历史指标保留期。
     *
     * @return 正数保留期
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Duration getRetention() { return retention; }

    /**
     * 设置总控历史指标保留期。
     *
     * @param retention 正数保留期
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setRetention(Duration retention) { this.retention = retention; }

    /**
     * 返回子控指标页上限。
     *
     * @return 有界页大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public int getPageLimit() { return pageLimit; }

    /**
     * 设置子控指标页上限。
     *
     * @param pageLimit 有界页大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setPageLimit(int pageLimit) { this.pageLimit = pageLimit; }

    /**
     * 返回部署注入的可轮换服务 Token。
     *
     * @return 服务 Token，可为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public String getApiToken() { return apiToken; }

    /**
     * 设置部署注入的可轮换服务 Token。
     *
     * @param apiToken 服务 Token
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
}
