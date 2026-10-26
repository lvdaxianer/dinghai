package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.sqlite.SQLiteDataSource;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 删除任务自动装配测试。 */
public class FileStorageDeleteAutoConfigurationTest {

    /** 测试数据库临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 默认同步模式不应要求 JDBC，也不应创建异步任务仓储。 */
    @Test
    public void shouldKeepSynchronousModeWithoutDatabaseConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStorageDeleteAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FileStorageDeleteTaskRepository.class));
    }

    /** SQLite 异步模式应自动创建任务表和仓储。 */
    @Test
    public void shouldCreateSqliteRepositoryOnlyWhenAsyncModeIsEnabled() throws Exception {
        Path database = temporaryFolder.newFile("delete-task.db").toPath();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStorageDeleteAutoConfiguration.class))
                .withPropertyValues("dinghai.file-storage.delete.mode=async",
                        "dinghai.file-storage.delete.database=sqlite",
                        "dinghai.file-storage.delete.sqlite-path=" + database)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageDeleteTaskRepository.class);
                    assertThat(context.getBean(FileStorageProperties.class).getDelete().getMode())
                            .isEqualTo(FileStorageDeleteMode.ASYNC);
                });
    }

    /** JDBC 异步模式缺少宿主数据源时应明确失败。 */
    @Test
    public void shouldRejectJdbcModeWithoutHostDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStorageDeleteAutoConfiguration.class))
                .withPropertyValues("dinghai.file-storage.delete.mode=async",
                        "dinghai.file-storage.delete.database=jdbc")
                .run(context -> assertThat(context).hasFailed());
    }

    /** JDBC 模式应复用宿主提供的数据源，而不是强制创建 SQLite 文件。 */
    @Test
    public void shouldReuseHostDataSourceForJdbcMode() throws Exception {
        Path database = temporaryFolder.newFile("host-delete-task.db").toPath();
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStorageDeleteAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withPropertyValues("dinghai.file-storage.delete.mode=async",
                        "dinghai.file-storage.delete.database=jdbc")
                .run(context -> assertThat(context).hasSingleBean(FileStorageDeleteTaskRepository.class));
    }

    /** 异步模式在存在文件存储时才暴露提交服务和独立 Worker。 */
    @Test
    public void shouldExposeDeleteServiceAndWorkerOnlyForAsyncStorage() throws Exception {
        Path database = temporaryFolder.newFile("service-delete-task.db").toPath();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FileStorageDeleteAutoConfiguration.class))
                .withBean(FileStorage.class, () -> mock(FileStorage.class))
                .withPropertyValues("dinghai.file-storage.storage-alias=documents",
                        "dinghai.file-storage.delete.mode=async",
                        "dinghai.file-storage.delete.sqlite-path=" + database,
                        "dinghai.file-storage.delete.poll-interval=1h")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageDeleteService.class);
                    assertThat(context).hasSingleBean(FileStorageDeleteWorker.class);
                });
    }
}
