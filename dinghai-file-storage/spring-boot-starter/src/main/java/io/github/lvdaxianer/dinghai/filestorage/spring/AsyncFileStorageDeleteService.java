package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;

/** 将删除请求登记到任务仓储的异步服务。 */
public final class AsyncFileStorageDeleteService implements FileStorageDeleteService {
    private final FileStorageDeleteTaskRepository repository;
    private final String storageAlias;
    private final Clock clock;

    /** 创建异步删除服务。 */
    public AsyncFileStorageDeleteService(FileStorage storage, FileStorageDeleteTaskRepository repository,
                                         FileStorageProperties properties, Clock clock) {
        Objects.requireNonNull(storage, "storage");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.storageAlias = requireText(properties.getStorageAlias(), "storage alias");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 登记任务；并发重复登记时返回已存在任务。 */
    @Override
    public FileStorageDeleteTaskView submit(FileStorageDeleteRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<FileStorageDeleteTask> existing = repository.findByIdempotencyKey(
                storageAlias, request.idempotencyKey());
        if (existing.isPresent()) {
            return FileStorageDeleteTaskView.from(existing.orElseThrow());
        }
        long now = clock.millis();
        FileStorageDeleteTask task = FileStorageDeleteTask.pending(UUID.randomUUID().toString(), storageAlias,
                request.key().value(), request.idempotencyKey(), now);
        if (repository.insert(task)) {
            return FileStorageDeleteTaskView.from(task);
        }
        return repository.findByIdempotencyKey(storageAlias, request.idempotencyKey())
                .map(FileStorageDeleteTaskView::from)
                .orElseThrow(() -> new IllegalStateException("delete task registration was not persisted"));
    }

    /** 查询任务状态。 */
    @Override
    public Optional<FileStorageDeleteTaskView> status(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTaskId(taskId).map(FileStorageDeleteTaskView::from);
    }

    /** 保证错误配置不会把空别名写入任务表。 */
    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
