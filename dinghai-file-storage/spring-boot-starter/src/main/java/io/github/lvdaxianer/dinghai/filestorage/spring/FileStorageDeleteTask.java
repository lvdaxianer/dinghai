package io.github.lvdaxianer.dinghai.filestorage.spring;

/** 持久化的文件删除任务快照。 */
public record FileStorageDeleteTask(
        String taskId,
        String storageAlias,
        String objectKey,
        String idempotencyKey,
        FileStorageDeleteTaskStatus status,
        int attemptCount,
        long nextRunAt,
        String workerId,
        String leaseToken,
        Long leaseUntil,
        String lastError,
        long createdAt,
        long updatedAt,
        Long completedAt) {

    /** 创建待执行任务。 */
    public static FileStorageDeleteTask pending(String taskId, String storageAlias, String objectKey,
                                                String idempotencyKey, long now) {
        return new FileStorageDeleteTask(taskId, storageAlias, objectKey, idempotencyKey,
                FileStorageDeleteTaskStatus.PENDING, 0, now, null, null, null, null, now, now, null);
    }
}
