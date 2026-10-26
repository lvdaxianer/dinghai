package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/** 异步删除任务持久化自动装配；默认同步模式不会触碰数据库。 */
@AutoConfiguration
@AutoConfigureAfter(FileStorageAutoConfiguration.class)
@EnableConfigurationProperties(FileStorageProperties.class)
@ConditionalOnProperty(name = "dinghai.file-storage.delete.mode", havingValue = "async")
public class FileStorageDeleteAutoConfiguration {

    /** 创建 SQLite 或宿主 JDBC 数据源对应的任务仓储。 */
    @Bean
    @ConditionalOnMissingBean(FileStorageDeleteTaskRepository.class)
    public FileStorageDeleteTaskRepository fileStorageDeleteTaskRepository(
            FileStorageProperties properties, ObjectProvider<DataSource> dataSources) {
        FileStorageProperties.Delete delete = properties.getDelete();
        if (delete.getDatabase() == FileStorageDeleteDatabase.SQLITE) {
            if (delete.getSqlitePath() == null) {
                throw new IllegalStateException("dinghai.file-storage.delete.sqlite-path is required");
            }
            return FileStorageDeleteTaskRepository.sqlite(delete.getSqlitePath());
        }
        DataSource dataSource = dataSources.getIfAvailable();
        if (dataSource == null) {
            throw new IllegalStateException(
                    "a DataSource is required when dinghai.file-storage.delete.database=jdbc");
        }
        return FileStorageDeleteTaskRepository.jdbc(dataSource);
    }

    /** 暴露异步删除提交与状态查询服务。 */
    @Bean
    @ConditionalOnBean(FileStorage.class)
    @ConditionalOnMissingBean(FileStorageDeleteService.class)
    public FileStorageDeleteService fileStorageDeleteService(
            FileStorage storage, FileStorageDeleteTaskRepository repository,
            FileStorageProperties properties) {
        return new AsyncFileStorageDeleteService(storage, repository, properties, Clock.systemUTC());
    }

    /** 注册独立的删除轮询器和有界执行器。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean({FileStorage.class, FileStorageDeleteService.class})
    @ConditionalOnMissingBean(FileStorageDeleteWorker.class)
    public FileStorageDeleteWorker fileStorageDeleteWorker(
            FileStorage storage, FileStorageDeleteTaskRepository repository,
            FileStorageProperties properties, ObjectProvider<TaskDecorator> taskDecorators) {
        return new FileStorageDeleteWorker(storage, repository, properties, Clock.systemUTC(),
                taskDecorators.getIfAvailable(() -> task -> task));
    }
}
