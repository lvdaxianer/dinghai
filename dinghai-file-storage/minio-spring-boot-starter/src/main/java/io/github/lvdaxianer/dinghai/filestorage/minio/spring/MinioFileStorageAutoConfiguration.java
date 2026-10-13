package io.github.lvdaxianer.dinghai.filestorage.minio.spring;

import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MinIO 文件存储专用自动配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
@AutoConfiguration
@ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "minio")
public class MinioFileStorageAutoConfiguration {

    /** 注册 MinIO provider factory。 */
    @Bean
    @ConditionalOnMissingBean(name = "minioFileStorageProviderFactory")
    public FileStorageProviderFactory minioFileStorageProviderFactory() {
        return new MinioFileStorageProviderFactory();
    }
}
