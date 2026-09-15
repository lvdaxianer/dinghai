package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.governance.api.MetricPage;
import io.github.lvdaxianer.dinghai.threadpool.governance.api.ThreadPoolMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 用 SQLite 保存子控指标并按单调主键分页读取。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class SqliteMetricStore {
    private final String url;
    private final int maximumPageSize;

    /** 创建并初始化本地 SQLite 指标库。 */
    public SqliteMetricStore(Path path, int maximumPageSize) {
        validate(path, maximumPageSize);
        createParent(path);
        url = "jdbc:sqlite:" + path.toAbsolutePath();
        this.maximumPageSize = maximumPageSize;
        initialize();
    }

    /** 持久化一个采样指标。 */
    public void append(ThreadPoolMetric metric) {
        String sql = "insert into thread_pool_metric(sampled_at,pool_name,active,pool_size,queue_size,actual_max,submitted,completed,rejected,failed,wait_ms,execution_ms,priority,reliability) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindMetric(statement, metric);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot persist thread pool metric", exception);
        }
    }

    /** 按 cursor 返回有界的一页指标。 */
    public MetricPage page(String cursor, int requestedLimit) {
        long after = cursor == null || cursor.isBlank() ? 0 : Long.parseLong(cursor);
        int limit = Math.min(Math.max(1, requestedLimit), maximumPageSize);
        List<ThreadPoolMetric> metrics = query(after, limit + 1);
        boolean hasMore = metrics.size() > limit;
        List<ThreadPoolMetric> page = hasMore ? metrics.subList(0, limit) : metrics;
        String next = page.isEmpty() ? String.valueOf(after) : String.valueOf(page.getLast().metricId());
        return new MetricPage(next, hasMore, oldestCursor(), List.copyOf(page));
    }

    /** 删除超过保留期的指标。 */
    public void purge(Duration retention) {
        long threshold = System.currentTimeMillis() - retention.toMillis();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("delete from thread_pool_metric where sampled_at < ?")) {
            statement.setLong(1, threshold);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot purge thread pool metrics", exception);
        }
    }

    private void initialize() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("create table if not exists thread_pool_metric(metric_id integer primary key autoincrement,sampled_at integer not null,pool_name text not null,active integer not null,pool_size integer not null,queue_size integer not null,actual_max integer not null,submitted integer not null,completed integer not null,rejected integer not null,failed integer not null,wait_ms integer not null,execution_ms integer not null,priority text not null,reliability text not null)")) {
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot initialize thread pool metric database", exception);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        configureConnection(connection);
        return connection;
    }

    private void configureConnection(Connection connection) throws SQLException {
        try (PreparedStatement wal = connection.prepareStatement("pragma journal_mode=WAL");
             PreparedStatement timeout = connection.prepareStatement("pragma busy_timeout=5000")) {
            wal.execute();
            timeout.execute();
        }
    }

    private List<ThreadPoolMetric> query(long after, int limit) {
        List<ThreadPoolMetric> metrics = new ArrayList<>(limit);
        String sql = "select * from thread_pool_metric where metric_id > ? order by metric_id limit ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, after);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) { metrics.add(metric(result)); }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot query thread pool metrics", exception);
        }
        return metrics;
    }

    private String oldestCursor() {
        try (Connection connection = connection(); ResultSet result = connection.createStatement().executeQuery("select coalesce(min(metric_id),0) from thread_pool_metric")) {
            return result.next() ? String.valueOf(result.getLong(1)) : "0";
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot query oldest cursor", exception);
        }
    }

    private void bindMetric(PreparedStatement statement, ThreadPoolMetric metric) throws SQLException {
        statement.setLong(1, metric.sampledAt()); statement.setString(2, metric.poolName());
        statement.setInt(3, metric.active()); statement.setInt(4, metric.poolSize());
        statement.setInt(5, metric.queueSize()); statement.setInt(6, metric.actualMaximum());
        statement.setLong(7, metric.submitted()); statement.setLong(8, metric.completed());
        statement.setLong(9, metric.rejected()); statement.setLong(10, metric.failed());
        statement.setLong(11, metric.waitMillis()); statement.setLong(12, metric.executionMillis());
        statement.setString(13, metric.priority()); statement.setString(14, metric.reliability());
    }

    private ThreadPoolMetric metric(ResultSet result) throws SQLException {
        return new ThreadPoolMetric(result.getLong("metric_id"), result.getLong("sampled_at"),
                result.getString("pool_name"), result.getInt("active"), result.getInt("pool_size"),
                result.getInt("queue_size"), result.getInt("actual_max"), result.getLong("submitted"),
                result.getLong("completed"), result.getLong("rejected"), result.getLong("failed"),
                result.getLong("wait_ms"), result.getLong("execution_ms"), result.getString("priority"),
                result.getString("reliability"));
    }

    private void createParent(Path path) {
        try { Files.createDirectories(path.toAbsolutePath().getParent()); } catch (Exception exception) { throw new IllegalArgumentException("cannot create SQLite parent directory", exception); }
    }

    private void validate(Path path, int maximumPageSize) {
        if (path == null || maximumPageSize < 1) { throw new IllegalArgumentException("invalid SQLite metric store configuration"); }
    }
}
