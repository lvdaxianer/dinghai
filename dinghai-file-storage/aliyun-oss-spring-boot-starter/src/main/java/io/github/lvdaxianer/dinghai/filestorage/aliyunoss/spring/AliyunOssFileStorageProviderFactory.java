package io.github.lvdaxianer.dinghai.filestorage.aliyunoss.spring;

import com.aliyun.sdk.service.oss2.OSSClient;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProvider;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;

/** 创建阿里云 OSS 文件存储适配器的 provider factory。 */
final class AliyunOssFileStorageProviderFactory implements FileStorageProviderFactory {

    private final OSSClient client;

    /** 创建绑定受管客户端的 factory。 */
    AliyunOssFileStorageProviderFactory(OSSClient client) {
        this.client = client;
    }

    @Override
    public FileStorageProvider provider() {
        return FileStorageProvider.ALIYUN_OSS;
    }

    @Override
    public FileStorage create(FileStorageProperties ignored, String bucket) {
        return new AliyunOssFileStorageAdapter(client, bucket);
    }
}
