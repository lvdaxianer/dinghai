package io.github.lvdaxianer.dinghai.threadpool.governance.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

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
        Objects.requireNonNull(path, "path");
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

    /**
     * 返回当前启用实例，供单实例轮询器和管理 API 查询。
     *
     * @return 按实例标识排序的不可变实例列表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public List<GovernanceInstance> instances() {
        List<GovernanceInstance> instances = new ArrayList<>();
        String sql = "select instance_id,base_url,environment,enabled,updated_at from governance_instance order by instance_id";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) { instances.add(instance(result)); }
            return List.copyOf(instances);
        } catch (SQLException exception) { throw failure("list instances", exception); }
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

    /**
     * 返回一个实例全部采集缺口，供管理查询和告警恢复使用。
     *
     * @param instanceId 子控实例标识
     * @return 按发现时间排序的缺口记录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public List<GovernanceCollectionGap> gaps(String instanceId) {
        List<GovernanceCollectionGap> gaps = new ArrayList<>();
        String sql = "select id,instance_id,requested_cursor,oldest_cursor,detected_at,recovery_status from governance_collection_gap where instance_id=? order by id";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instanceId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) { gaps.add(gap(result)); }
            }
            return List.copyOf(gaps);
        } catch (SQLException exception) { throw failure("list collection gaps", exception); }
    }

    /**
     * 将尚未恢复的缺口标记为已用快照恢复。
     *
     * @param instanceId 子控实例标识
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void markLatestGapRecovered(String instanceId) {
        String sql = "update governance_collection_gap set recovery_status='RECOVERED' where id=(select max(id) from governance_collection_gap where instance_id=? and recovery_status='OPEN')";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instanceId);
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("mark collection gap recovered", exception); }
    }

    /**
     * 创建或刷新一个开放告警，保留首次触发时间。
     *
     * @param alertKey 稳定告警键
     * @param value 当前安全摘要值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void fireAlert(String alertKey, String value) {
        long now = System.currentTimeMillis();
        String sql = "insert into governance_alert_state(alert_key,status,first_fired_at,last_fired_at,last_value,resolved_at) values(?,?,?,?,?,null) on conflict(alert_key) do update set status='OPEN',last_fired_at=excluded.last_fired_at,last_value=excluded.last_value,resolved_at=null";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alertKey); statement.setString(2, "OPEN");
            statement.setLong(3, now); statement.setLong(4, now); statement.setString(5, value);
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("fire alert", exception); }
    }

    /**
     * 将一个已恢复的告警标记为已解决。
     *
     * @param alertKey 稳定告警键
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public void resolveAlert(String alertKey) {
        String sql = "update governance_alert_state set status='RESOLVED',resolved_at=? where alert_key=? and status='OPEN'";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, System.currentTimeMillis()); statement.setString(2, alertKey);
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("resolve alert", exception); }
    }

    /**
     * 读取一个告警的当前持久化状态。
     *
     * @param alertKey 稳定告警键
     * @return 可选告警状态
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Optional<GovernanceAlertState> alert(String alertKey) {
        String sql = "select alert_key,status,first_fired_at,last_fired_at,last_value,resolved_at from governance_alert_state where alert_key=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alertKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(alert(result)) : Optional.empty();
            }
        } catch (SQLException exception) { throw failure("read alert", exception); }
    }

    private void initialize() {
        execute("create table if not exists governance_instance(instance_id text primary key,base_url text not null,environment text not null,enabled integer not null,updated_at integer not null)");
        execute("create table if not exists governance_cursor(instance_id text primary key,cursor text not null,updated_at integer not null)");
        execute("create table if not exists governance_collection_gap(id integer primary key autoincrement,instance_id text not null,requested_cursor text,oldest_cursor text,detected_at integer not null,recovery_status text not null)");
        execute("create table if not exists governance_alert_state(alert_key text primary key,status text not null,first_fired_at integer not null,last_fired_at integer not null,last_value text,resolved_at integer)");
    }

    /**
     * 从查询行构建不泄露控制面内部状态的实例投影。
     *
     * @param result SQLite 查询结果
     * @return 实例投影
     * @throws SQLException 读取结果失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private GovernanceInstance instance(ResultSet result) throws SQLException {
        return new GovernanceInstance(result.getString("instance_id"), result.getString("base_url"),
                result.getString("environment"), result.getBoolean("enabled"), result.getLong("updated_at"));
    }

    /**
     * 从查询行构建采集缺口投影。
     *
     * @param result SQLite 查询结果
     * @return 采集缺口投影
     * @throws SQLException 读取结果失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private GovernanceCollectionGap gap(ResultSet result) throws SQLException {
        return new GovernanceCollectionGap(result.getLong("id"), result.getString("instance_id"),
                result.getString("requested_cursor"), result.getString("oldest_cursor"),
                result.getLong("detected_at"), result.getString("recovery_status"));
    }

    /**
     * 从查询行构建告警状态投影。
     *
     * @param result SQLite 查询结果
     * @return 告警状态投影
     * @throws SQLException 读取结果失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private GovernanceAlertState alert(ResultSet result) throws SQLException {
        long resolvedAt = result.getLong("resolved_at");
        Long resolved = result.wasNull() ? null : resolvedAt;
        return new GovernanceAlertState(result.getString("alert_key"), result.getString("status"),
                result.getLong("first_fired_at"), result.getLong("last_fired_at"),
                result.getString("last_value"), resolved);
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
