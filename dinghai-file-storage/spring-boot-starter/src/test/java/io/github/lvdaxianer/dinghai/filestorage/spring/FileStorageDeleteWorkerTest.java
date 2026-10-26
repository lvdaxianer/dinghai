package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** 删除任务 Worker 测试。 */
public class FileStorageDeleteWorkerTest {

    /** 测试数据库临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** provider 删除成功时任务应进入 SUCCESS。 */
    @Test
    public void shouldMarkTaskSuccessfulAfterProviderDelete() throws Exception {
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-1", "documents", "a.txt", "request-1", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenReturn(true);

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties(), clock());

        assertThat(worker.processOnce(1_000L)).isTrue();
        assertThat(repository.findByTaskId("task-1").orElseThrow().status())
                .isEqualTo(FileStorageDeleteTaskStatus.SUCCESS);
    }

    /** provider 返回对象不存在时也应视为幂等成功。 */
    @Test
    public void shouldTreatMissingObjectAsSuccessfulDelete() throws Exception {
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-1", "documents", "missing.txt", "request-1", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenReturn(false);

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties(), clock());

        worker.processOnce(1_000L);

        assertThat(repository.findByTaskId("task-1").orElseThrow().status())
                .isEqualTo(FileStorageDeleteTaskStatus.SUCCESS);
    }

    /** 可恢复异常应进入重试等待，且错误记录不能泄露异常消息。 */
    @Test
    public void shouldScheduleSafeRetryAfterRecoverableFailure() throws Exception {
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-1", "documents", "a.txt", "request-1", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenThrow(new IllegalStateException("secret-endpoint-token"));

        FileStorageProperties properties = properties();
        properties.getDelete().setRetryBackoff(java.time.Duration.ofMillis(500L));
        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties, clock());

        worker.processOnce(1_000L);

        FileStorageDeleteTask task = repository.findByTaskId("task-1").orElseThrow();
        assertThat(task.status()).isEqualTo(FileStorageDeleteTaskStatus.RETRY_WAIT);
        assertThat(task.nextRunAt()).isEqualTo(1_500L);
        assertThat(task.lastError()).doesNotContain("secret-endpoint-token");
    }

    /** 达到最大尝试次数后任务应进入最终失败。 */
    @Test
    public void shouldMarkTaskFailedAfterMaximumAttempts() throws Exception {
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-1", "documents", "a.txt", "request-1", 1_000L));
        FileStorage storage = mock(FileStorage.class);
        when(storage.delete(any(ObjectKey.class))).thenThrow(new IllegalStateException("secret-endpoint-token"));
        FileStorageProperties properties = properties();
        properties.getDelete().setMaxAttempts(1);

        FileStorageDeleteWorker worker = new FileStorageDeleteWorker(storage, repository, properties, clock());

        worker.processOnce(1_000L);

        FileStorageDeleteTask task = repository.findByTaskId("task-1").orElseThrow();
        assertThat(task.status()).isEqualTo(FileStorageDeleteTaskStatus.FAILED);
        assertThat(task.lastError()).isEqualTo("IllegalStateException");
    }

    /** 新 Worker 应能接管前一个 Worker 留下的重试任务。 */
    @Test
    public void shouldResumeRetryTaskAfterWorkerRestart() throws Exception {
        FileStorageDeleteTaskRepository repository = repository();
        repository.insert(FileStorageDeleteTask.pending("task-1", "documents", "a.txt", "request-1", 1_000L));
        FileStorage firstStorage = mock(FileStorage.class);
        when(firstStorage.delete(any(ObjectKey.class))).thenThrow(new IllegalStateException("temporary"));
        FileStorageProperties properties = properties();
        properties.getDelete().setRetryBackoff(java.time.Duration.ofMillis(500L));
        FileStorageDeleteWorker firstWorker = new FileStorageDeleteWorker(firstStorage, repository, properties, clock());

        firstWorker.processOnce(1_000L);

        FileStorage secondStorage = mock(FileStorage.class);
        when(secondStorage.delete(any(ObjectKey.class))).thenReturn(true);
        FileStorageDeleteWorker secondWorker = new FileStorageDeleteWorker(secondStorage, repository, properties, clock());
        secondWorker.processOnce(1_500L);

        assertThat(repository.findByTaskId("task-1").orElseThrow().status())
                .isEqualTo(FileStorageDeleteTaskStatus.SUCCESS);
        verify(secondStorage).delete(any(ObjectKey.class));
    }

    private FileStorageDeleteTaskRepository repository() throws Exception {
        Path database = temporaryFolder.newFile("delete-task-" + System.nanoTime() + ".db").toPath();
        return FileStorageDeleteTaskRepository.sqlite(database);
    }

    private FileStorageProperties properties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setStorageAlias("documents");
        properties.getDelete().setMode(FileStorageDeleteMode.ASYNC);
        properties.getDelete().setLeaseDuration(java.time.Duration.ofSeconds(30L));
        properties.getDelete().setMaxAttempts(3);
        return properties;
    }

    private Clock clock() {
        return Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
    }
}
