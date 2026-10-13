package io.github.lvdaxianer.dinghai.filestorage.aliyunoss.spring;

import com.aliyun.sdk.service.oss2.OSSClient;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssClientFactory;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云 OSS 文件存储专用自动配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
@AutoConfiguration
@ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "aliyun-oss")
public class AliyunOssFileStorageAutoConfiguration {

    /** 创建由 Spring 管理生命周期的 OSS SDK 客户端。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OSSClient.class)
    public OSSClient aliyunOssClient(FileStorageProperties properties) {
        try {
            return AliyunOssClientFactory.create(configuration(properties));
        } catch (IllegalArgumentException exception) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "aliyun oss configuration is incomplete", exception);
        }
    }

    /** 注册阿里云 OSS provider factory。 */
    @Bean
    @ConditionalOnMissingBean(name = "aliyunOssFileStorageProviderFactory")
    public FileStorageProviderFactory aliyunOssFileStorageProviderFactory(
            FileStorageProperties properties, OSSClient client) {
        try {
            configuration(properties);
            return new AliyunOssFileStorageProviderFactory(client);
        } catch (IllegalArgumentException exception) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "aliyun oss configuration is incomplete", exception);
        }
    }

    private AliyunOssStorageConfiguration configuration(FileStorageProperties properties) {
        FileStorageProperties.AliyunOss oss = properties.getAliyunOss();
        return new AliyunOssStorageConfiguration(oss.getRegion(), properties.getBucket(),
                oss.getConnectTimeout(), oss.getReadTimeout(), oss.getWriteTimeout());
    }
}
