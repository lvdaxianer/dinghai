package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.spring;

import com.obs.services.IObsCredentialsProvider;
import com.obs.services.ObsClient;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.HuaweiObsClientFactory;
import io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.HuaweiObsStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 华为云 OBS 文件存储专用自动配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
@AutoConfiguration
@ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "huaweicloud-obs")
public class HuaweiObsFileStorageAutoConfiguration {

    /** 创建由 Spring 管理生命周期的 OBS SDK 客户端。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ObsClient.class)
    public ObsClient huaweicloudObsClient(FileStorageProperties properties,
                                          ObjectProvider<IObsCredentialsProvider> providers) {
        HuaweiObsStorageConfiguration configuration = configuration(properties);
        try {
            if (configuration.credentialMode()
                    == io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.HuaweiObsCredentialMode.PROVIDER) {
                IObsCredentialsProvider provider = providers.getIfAvailable();
                if (provider == null) {
                    throw new IllegalArgumentException("OBS credentials provider is required");
                }
                return HuaweiObsClientFactory.create(configuration, provider);
            }
            return HuaweiObsClientFactory.create(configuration);
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("huaweicloud obs configuration is incomplete", exception);
        }
    }

    /** 注册华为云 OBS provider factory。 */
    @Bean
    @ConditionalOnMissingBean(name = "huaweiObsFileStorageProviderFactory")
    public FileStorageProviderFactory huaweiObsFileStorageProviderFactory(
            FileStorageProperties properties, ObsClient client) {
        configuration(properties);
        return new HuaweiObsFileStorageProviderFactory(client);
    }

    private HuaweiObsStorageConfiguration configuration(FileStorageProperties properties) {
        FileStorageProperties.HuaweiCloudObs obs = properties.getHuaweicloudObs();
        try {
            return new HuaweiObsStorageConfiguration(obs.getEndpoint(), obs.getRegion(),
                    properties.getBucket(), obs.getCredentialMode(), obs.getAccessKey(),
                    obs.getSecretKey(), obs.getSecurityToken(), obs.getConnectTimeout(),
                    obs.getReadTimeout(), obs.getWriteTimeout());
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("huaweicloud obs configuration is incomplete", exception);
        }
    }

    private FileStorageException invalidConfiguration(String message, Throwable cause) {
        return new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION, message, cause);
    }
}
