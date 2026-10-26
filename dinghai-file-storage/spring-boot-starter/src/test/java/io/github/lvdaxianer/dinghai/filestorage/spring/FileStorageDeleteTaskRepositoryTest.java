package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

/** 文件删除任务 SQLite 持久化测试。 */
public class FileStorageDeleteTaskRepositoryTest {

    /** 测试数据库临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** SQLite 仓储必须支持登记、幂等查询与状态更新。 */
    @Test
    public void shouldPersistAndClaimDeleteTask() throws Exception {
        Path database = temporaryFolder.newFile("delete-task.db").toPath();
        FileStorageDeleteTaskRepository repository =
                FileStorageDeleteTaskRepository.sqlite(database);
        long now = 1_000L;
        FileStorageDeleteTask task = FileStorageDeleteTask.pending(
                "task-1", "documents", "a/b.txt", "request-1", now);

        assertThat(repository.insert(task)).isTrue();
        assertThat(repository.insert(task)).isFalse();
        assertThat(repository.findByIdempotencyKey("documents", "request-1"))
                .contains(task);

        Optional<FileStorageDeleteTask> claimed = repository.claimNext(
                now, "worker-1", "lease-1", 2_000L);

        assertThat(claimed).isPresent();
        assertThat(claimed.orElseThrow().status()).isEqualTo(FileStorageDeleteTaskStatus.RUNNING);
        assertThat(claimed.orElseThrow().workerId()).isEqualTo("worker-1");
        assertThat(repository.claimNext(1_500L, "worker-2", "lease-2", 2_500L)).isEmpty();
        assertThat(repository.claimNext(2_001L, "worker-2", "lease-2", 3_001L)).isPresent();
        assertThat(repository.markSuccess("task-1", "lease-1", 2_100L)).isFalse();
        assertThat(repository.markSuccess("task-1", "lease-2", 2_100L)).isTrue();
        FileStorageDeleteTaskRepository reopened = FileStorageDeleteTaskRepository.sqlite(database);
        assertThat(reopened.findByTaskId("task-1").orElseThrow().status())
                .isEqualTo(FileStorageDeleteTaskStatus.SUCCESS);
    }
}
