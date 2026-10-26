package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Path;
import java.util.Optional;
import javax.sql.DataSource;

/** 文件删除任务的持久化边界。 */
public interface FileStorageDeleteTaskRepository {

    /** 使用 SQLite 创建任务仓储，供单机部署与测试使用。 */
    static FileStorageDeleteTaskRepository sqlite(Path path) {
        return JdbcFileStorageDeleteTaskRepository.sqlite(path);
    }

    /** 使用宿主应用提供的 JDBC 数据源创建任务仓储。 */
    static FileStorageDeleteTaskRepository jdbc(DataSource dataSource) {
        return JdbcFileStorageDeleteTaskRepository.jdbc(dataSource);
    }

    /** 登记任务；任务 ID 或幂等键已存在时返回 false。 */
    boolean insert(FileStorageDeleteTask task);

    /** 按任务 ID 查询。 */
    Optional<FileStorageDeleteTask> findByTaskId(String taskId);

    /** 按业务幂等键查询。 */
    Optional<FileStorageDeleteTask> findByIdempotencyKey(String storageAlias, String idempotencyKey);

    /** 原子领取一个到期任务。 */
    Optional<FileStorageDeleteTask> claimNext(long now, String workerId, String leaseToken, long leaseUntil);

    /** 使用租约令牌标记成功。 */
    boolean markSuccess(String taskId, String leaseToken, long now);

    /** 使用租约令牌安排下一次重试。 */
    boolean markRetry(String taskId, String leaseToken, long nextRunAt, String lastError, long now);

    /** 使用租约令牌标记最终失败。 */
    boolean markFailed(String taskId, String leaseToken, String lastError, long now);
}
