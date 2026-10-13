package io.github.lvdaxianer.dinghai.filestorage.minio.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProvider;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;

/** 创建 MinIO 文件存储适配器的 provider factory。 */
final class MinioFileStorageProviderFactory implements FileStorageProviderFactory {

    /** 创建无状态 MinIO factory。 */
    MinioFileStorageProviderFactory() {
    }

    @Override
    public FileStorageProvider provider() {
        return FileStorageProvider.MINIO;
    }

    @Override
    public FileStorage create(FileStorageProperties properties, String bucket) {
        FileStorageProperties.Minio minio = properties.getMinio();
        try {
            return new MinioFileStorageAdapter(new MinioStorageConfiguration(minio.getEndpoint(),
                    bucket, minio.getAccessKey(), minio.getSecretKey(), minio.getConnectTimeout(),
                    minio.getReadTimeout(), minio.getWriteTimeout()));
        } catch (IllegalArgumentException exception) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "minio configuration is incomplete", exception);
        }
    }
}
