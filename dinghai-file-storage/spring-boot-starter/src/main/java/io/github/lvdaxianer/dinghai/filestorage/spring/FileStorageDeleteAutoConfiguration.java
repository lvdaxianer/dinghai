package io.github.lvdaxianer.dinghai.filestorage.spring;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 异步删除任务持久化自动装配；默认同步模式不会触碰数据库。 */
@AutoConfiguration
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
}
