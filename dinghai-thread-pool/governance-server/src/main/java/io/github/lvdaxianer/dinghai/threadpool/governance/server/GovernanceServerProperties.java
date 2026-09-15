package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.nio.file.Path;
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
}
