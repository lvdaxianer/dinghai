package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 异步删除服务测试。 */
public class AsyncFileStorageDeleteServiceTest {

    /** 测试数据库临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 相同幂等键只能登记一个任务，并可查询状态。 */
    @Test
    public void shouldSubmitIdempotentlyAndQueryStatus() throws Exception {
        Path database = temporaryFolder.newFile("delete-task.db").toPath();
        FileStorageDeleteTaskRepository repository = FileStorageDeleteTaskRepository.sqlite(database);
        FileStorageProperties properties = properties();
        AsyncFileStorageDeleteService service = new AsyncFileStorageDeleteService(
                mock(FileStorage.class), repository, properties,
                Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));

        FileStorageDeleteTaskView first = service.submit(
                new FileStorageDeleteRequest(ObjectKey.of("a/b.txt"), "request-1"));
        FileStorageDeleteTaskView duplicate = service.submit(
                new FileStorageDeleteRequest(ObjectKey.of("other.txt"), "request-1"));

        assertThat(first.taskId()).isEqualTo(duplicate.taskId());
        assertThat(first.status()).isEqualTo(FileStorageDeleteTaskStatus.PENDING);
        assertThat(service.status(first.taskId())).contains(first);
        assertThat(service.status("missing-task")).isEmpty();
    }

    private FileStorageProperties properties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setStorageAlias("documents");
        properties.getDelete().setMode(FileStorageDeleteMode.ASYNC);
        return properties;
    }
}
