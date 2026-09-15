package io.github.lvdaxianer.dinghai.threadpool.spring;

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

/**
 * 创建启用 WAL 的子控 SQLite MyBatis-Plus 会话工厂。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
final class ThreadPoolSqliteSessionFactory {
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private static final String SCHEMA = "io/github/lvdaxianer/dinghai/threadpool/spring/thread-pool-metric-schema.sql";

    private ThreadPoolSqliteSessionFactory() {
    }

    /** 创建并初始化子控持久化会话工厂。 */
    static SqlSessionFactory create(Path path) {
        DataSource dataSource = dataSource(path);
        initialize(dataSource);
        return sessionFactory(dataSource);
    }

    /** 配置 SQLite 驱动在每个连接上启用 WAL 与忙等待。 */
    private static DataSource dataSource(Path path) {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + path.toAbsolutePath());
        return dataSource;
    }

    /** 用外置 schema 初始化数据库，避免业务类内嵌 DDL。 */
    private static void initialize(DataSource dataSource) {
        try (var connection = dataSource.getConnection(); Reader reader = schemaReader()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot initialize thread pool SQLite schema", exception);
        }
    }

    /** 打开外置 SQLite schema 资源。 */
    private static Reader schemaReader() {
        return new InputStreamReader(ThreadPoolSqliteSessionFactory.class.getClassLoader().getResourceAsStream(SCHEMA),
                StandardCharsets.UTF_8);
    }

    /** 注册实体 Mapper 并构建 MyBatis-Plus 会话工厂。 */
    private static SqlSessionFactory sessionFactory(DataSource dataSource) {
        Environment environment = new Environment("thread-pool-sqlite", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        configuration.addInterceptor(paginationInterceptor());
        configuration.addMapper(ThreadPoolMetricMapper.class);
        configuration.addMapper(ThreadPoolMetricMetadataMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /** 配置 SQLite 方言的 MyBatis-Plus 分页拦截器。 */
    private static MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        return interceptor;
    }
}
