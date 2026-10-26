package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** 基于 Spring JDBC 的跨 SQLite/主流关系库删除任务仓储。 */
final class JdbcFileStorageDeleteTaskRepository implements FileStorageDeleteTaskRepository {
    private static final String TABLE = "dinghai_file_storage_delete_task";
    private static final String COLUMNS = "task_id, storage_alias, object_key, idempotency_key, status, "
            + "attempt_count, next_run_at, worker_id, lease_token, lease_until, last_error, created_at, "
            + "updated_at, completed_at";
    private static final String SCHEMA_RESOURCE =
            "io/github/lvdaxianer/dinghai/filestorage/spring/file-storage-delete-task-schema.sql";
    private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private JdbcFileStorageDeleteTaskRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        initialize(dataSource);
    }

    static FileStorageDeleteTaskRepository sqlite(Path path) {
        try {
            Path absolutePath = path.toAbsolutePath().normalize();
            if (absolutePath.getParent() != null) {
                Files.createDirectories(absolutePath.getParent());
            }
            SQLiteConfig config = new SQLiteConfig();
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
            SQLiteDataSource dataSource = new SQLiteDataSource(config);
            dataSource.setUrl("jdbc:sqlite:" + absolutePath);
            return new JdbcFileStorageDeleteTaskRepository(dataSource);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot initialize file delete SQLite database", exception);
        }
    }

    static FileStorageDeleteTaskRepository jdbc(DataSource dataSource) {
        return new JdbcFileStorageDeleteTaskRepository(dataSource);
    }

    @Override
    public boolean insert(FileStorageDeleteTask task) {
        String sql = "INSERT INTO " + TABLE + " (" + COLUMNS
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, task.taskId(), task.storageAlias(), task.objectKey(), task.idempotencyKey(),
                    task.status().name(), task.attemptCount(), task.nextRunAt(), task.workerId(), task.leaseToken(),
                    task.leaseUntil(), task.lastError(), task.createdAt(), task.updatedAt(), task.completedAt());
            return true;
        } catch (DataAccessException exception) {
            boolean alreadyExists = findByTaskId(task.taskId()).isPresent()
                    || findByIdempotencyKey(task.storageAlias(), task.idempotencyKey()).isPresent();
            if (alreadyExists) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public Optional<FileStorageDeleteTask> findByTaskId(String taskId) {
        return queryOne("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE task_id = ?", taskId);
    }

    @Override
    public Optional<FileStorageDeleteTask> findByIdempotencyKey(String storageAlias, String idempotencyKey) {
        return queryOne("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE storage_alias = ? AND idempotency_key = ?", storageAlias, idempotencyKey);
    }

    @Override
    public Optional<FileStorageDeleteTask> claimNext(long now, String workerId, String leaseToken, long leaseUntil) {
        return transactionTemplate.execute(status -> {
            List<String> candidates = jdbcTemplate.query(
                    "SELECT task_id FROM " + TABLE
                            + " WHERE (status = 'PENDING' OR (status = 'RETRY_WAIT' AND next_run_at <= ?)"
                            + " OR (status = 'RUNNING' AND lease_until IS NOT NULL AND lease_until <= ?))"
                            + " ORDER BY next_run_at, created_at LIMIT 32",
                    (resultSet, rowNum) -> resultSet.getString(1), now, now);
            String update = "UPDATE " + TABLE + " SET status = 'RUNNING', worker_id = ?, lease_token = ?, "
                    + "lease_until = ?, attempt_count = attempt_count + 1, updated_at = ? WHERE task_id = ? "
                    + "AND (status = 'PENDING' OR (status = 'RETRY_WAIT' AND next_run_at <= ?) "
                    + "OR (status = 'RUNNING' AND lease_until IS NOT NULL AND lease_until <= ?))";
            for (String taskId : candidates) {
                if (jdbcTemplate.update(update, workerId, leaseToken, leaseUntil, now, taskId, now, now) == 1) {
                    return findByTaskId(taskId);
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean markSuccess(String taskId, String leaseToken, long now) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET status = 'SUCCESS', worker_id = NULL, "
                        + "lease_token = NULL, lease_until = NULL, updated_at = ?, completed_at = ? "
                        + "WHERE task_id = ? AND status = 'RUNNING' AND lease_token = ?",
                now, now, taskId, leaseToken) == 1;
    }

    @Override
    public boolean markRetry(String taskId, String leaseToken, long nextRunAt, String lastError, long now) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET status = 'RETRY_WAIT', worker_id = NULL, "
                        + "lease_token = NULL, lease_until = NULL, next_run_at = ?, last_error = ?, updated_at = ? "
                        + "WHERE task_id = ? AND status = 'RUNNING' AND lease_token = ?",
                nextRunAt, lastError, now, taskId, leaseToken) == 1;
    }

    @Override
    public boolean markFailed(String taskId, String leaseToken, String lastError, long now) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET status = 'FAILED', worker_id = NULL, "
                        + "lease_token = NULL, lease_until = NULL, last_error = ?, updated_at = ?, completed_at = ? "
                        + "WHERE task_id = ? AND status = 'RUNNING' AND lease_token = ?",
                lastError, now, now, taskId, leaseToken) == 1;
    }

    private Optional<FileStorageDeleteTask> queryOne(String sql, Object... arguments) {
        List<FileStorageDeleteTask> tasks = jdbcTemplate.query(sql, this::map, arguments);
        return tasks.stream().findFirst();
    }

    private FileStorageDeleteTask map(ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        return new FileStorageDeleteTask(resultSet.getString("task_id"), resultSet.getString("storage_alias"),
                resultSet.getString("object_key"), resultSet.getString("idempotency_key"),
                FileStorageDeleteTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"), resultSet.getLong("next_run_at"),
                resultSet.getString("worker_id"), resultSet.getString("lease_token"),
                nullableLong(resultSet, "lease_until"), resultSet.getString("last_error"),
                resultSet.getLong("created_at"), resultSet.getLong("updated_at"),
                nullableLong(resultSet, "completed_at"));
    }

    private Long nullableLong(ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void initialize(DataSource dataSource) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource(SCHEMA_RESOURCE));
            DatabasePopulatorUtils.execute(populator, dataSource);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot initialize file delete task schema", exception);
        }
    }
}
