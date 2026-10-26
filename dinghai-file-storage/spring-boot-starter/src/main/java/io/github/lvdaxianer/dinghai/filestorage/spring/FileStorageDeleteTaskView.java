package io.github.lvdaxianer.dinghai.filestorage.spring;

/** 对调用方暴露的安全删除任务状态，不包含 lease 或数据库内部字段。 */
public record FileStorageDeleteTaskView(
        String taskId,
        FileStorageDeleteTaskStatus status,
        int attemptCount,
        String lastError,
        long createdAt,
        long updatedAt,
        Long completedAt) {

    /** 从内部任务快照创建安全视图。 */
    public static FileStorageDeleteTaskView from(FileStorageDeleteTask task) {
        return new FileStorageDeleteTaskView(task.taskId(), task.status(), task.attemptCount(),
                task.lastError(), task.createdAt(), task.updatedAt(), task.completedAt());
    }
}
