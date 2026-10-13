package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.spring;

import com.obs.services.ObsClient;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.HuaweiObsFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProvider;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;

/** 创建华为云 OBS 文件存储适配器的 provider factory。 */
final class HuaweiObsFileStorageProviderFactory implements FileStorageProviderFactory {

    private final ObsClient client;
    /** 创建绑定 OBS 客户端的 factory。 */
    HuaweiObsFileStorageProviderFactory(ObsClient client) {
        this.client = client;
    }

    @Override
    public FileStorageProvider provider() {
        return FileStorageProvider.HUAWEICLOUD_OBS;
    }

    @Override
    public FileStorage create(FileStorageProperties properties, String bucket) {
        return new HuaweiObsFileStorageAdapter(client, bucket,
                properties.getHuaweicloudObs().getEndpoint());
    }
}
