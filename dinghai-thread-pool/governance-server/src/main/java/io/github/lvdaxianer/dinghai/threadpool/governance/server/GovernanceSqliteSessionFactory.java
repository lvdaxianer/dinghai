package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** 创建启用 WAL 的总控 SQLite MyBatis-Plus 会话工厂。 */
final class GovernanceSqliteSessionFactory {
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private static final String SCHEMA = "io/github/lvdaxianer/dinghai/threadpool/governance/server/governance-schema.sql";

    private GovernanceSqliteSessionFactory() { }

    /** 创建并初始化总控持久化会话工厂。 */
    static SqlSessionFactory create(Path path) {
        DataSource dataSource = dataSource(path);
        initialize(dataSource);
        Environment environment = new Environment("governance-sqlite", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        configuration.addInterceptor(paginationInterceptor());
        configuration.addMapper(GovernanceMappers.Instance.class);
        configuration.addMapper(GovernanceMappers.Cursor.class);
        configuration.addMapper(GovernanceMappers.Gap.class);
        configuration.addMapper(GovernanceMappers.Alert.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /** 通过 MyBatis 脚本执行器初始化外置 SQLite schema。 */
    private static void initialize(DataSource dataSource) {
        try (var connection = dataSource.getConnection(); Reader reader = schemaReader()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot initialize governance SQLite schema", exception);
        }
    }

    /** 打开外置 SQLite schema 资源。 */
    private static Reader schemaReader() {
        return new InputStreamReader(GovernanceSqliteSessionFactory.class.getClassLoader().getResourceAsStream(SCHEMA),
                StandardCharsets.UTF_8);
    }

    /** 配置每个 SQLite 连接使用 WAL 与有界忙等待。 */
    private static DataSource dataSource(Path path) {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + path.toAbsolutePath());
        return dataSource;
    }

    /** 配置 SQLite 方言的 MyBatis-Plus 分页拦截器。 */
    private static MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        return interceptor;
    }
}
