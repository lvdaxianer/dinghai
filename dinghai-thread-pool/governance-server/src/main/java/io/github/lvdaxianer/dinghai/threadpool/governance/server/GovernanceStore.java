package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * 单实例总控的 SQLite 控制面存储。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class GovernanceStore {
    private final String url;

    /** 创建并初始化 SQLite 控制面存储。 */
    public GovernanceStore(Path path) {
        createParent(path);
        url = "jdbc:sqlite:" + path.toAbsolutePath();
        initialize();
    }

    /** 注册或更新一个子控实例。 */
    public void register(String instanceId, String baseUrl, String environment) {
        String sql = "insert into governance_instance(instance_id,base_url,environment,enabled,updated_at) values(?,?,?,1,?) on conflict(instance_id) do update set base_url=excluded.base_url,environment=excluded.environment,enabled=1,updated_at=excluded.updated_at";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instanceId); statement.setString(2, baseUrl);
            statement.setString(3, environment); statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("register instance", exception); }
    }

    /** 读取某实例已确认的 cursor。 */
    public Optional<String> cursor(String instanceId) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("select cursor from governance_cursor where instance_id=?")) {
            statement.setString(1, instanceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException exception) { throw failure("read cursor", exception); }
    }

    /** 在聚合成功后提交一个实例 cursor。 */
    public void commitCursor(String instanceId, String cursor) {
        String sql = "insert into governance_cursor(instance_id,cursor,updated_at) values(?,?,?) on conflict(instance_id) do update set cursor=excluded.cursor,updated_at=excluded.updated_at";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instanceId); statement.setString(2, cursor);
            statement.setLong(3, System.currentTimeMillis()); statement.executeUpdate();
        } catch (SQLException exception) { throw failure("commit cursor", exception); }
    }

    /** 记录一次无法补齐的指标采集缺口。 */
    public void recordGap(String instanceId, String requestedCursor, String oldestCursor) {
        String sql = "insert into governance_collection_gap(instance_id,requested_cursor,oldest_cursor,detected_at,recovery_status) values(?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instanceId); statement.setString(2, requestedCursor);
            statement.setString(3, oldestCursor); statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, "OPEN"); statement.executeUpdate();
        } catch (SQLException exception) { throw failure("record collection gap", exception); }
    }

    private void initialize() {
        execute("create table if not exists governance_instance(instance_id text primary key,base_url text not null,environment text not null,enabled integer not null,updated_at integer not null)");
        execute("create table if not exists governance_cursor(instance_id text primary key,cursor text not null,updated_at integer not null)");
        execute("create table if not exists governance_collection_gap(id integer primary key autoincrement,instance_id text not null,requested_cursor text,oldest_cursor text,detected_at integer not null,recovery_status text not null)");
    }

    private void execute(String sql) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException exception) { throw failure("initialize governance database", exception); }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (PreparedStatement wal = connection.prepareStatement("pragma journal_mode=WAL");
             PreparedStatement timeout = connection.prepareStatement("pragma busy_timeout=5000")) {
            wal.execute(); timeout.execute();
        }
        return connection;
    }

    private void createParent(Path path) {
        try { Files.createDirectories(path.toAbsolutePath().getParent()); }
        catch (Exception exception) { throw new IllegalArgumentException("cannot create governance database directory", exception); }
    }

    private IllegalStateException failure(String action, SQLException exception) {
        return new IllegalStateException("cannot " + action, exception);
    }
}
