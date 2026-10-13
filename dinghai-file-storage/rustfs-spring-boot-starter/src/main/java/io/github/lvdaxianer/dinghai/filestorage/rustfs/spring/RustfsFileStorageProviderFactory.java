package io.github.lvdaxianer.dinghai.filestorage.rustfs.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.rustfs.RustfsFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.rustfs.RustfsStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProvider;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProperties;

/** 创建 RustFS 文件存储适配器的 provider factory。 */
final class RustfsFileStorageProviderFactory implements FileStorageProviderFactory {

    /** 创建无状态 RustFS factory。 */
    RustfsFileStorageProviderFactory() {
    }

    @Override
    public FileStorageProvider provider() {
        return FileStorageProvider.RUSTFS;
    }

    @Override
    public FileStorage create(FileStorageProperties properties, String bucket) {
        FileStorageProperties.Rustfs rustfs = properties.getRustfs();
        try {
            return new RustfsFileStorageAdapter(new RustfsStorageConfiguration(rustfs.getEndpoint(),
                    bucket, rustfs.getAccessKey(), rustfs.getSecretKey(), rustfs.getConnectTimeout(),
                    rustfs.getReadTimeout(), rustfs.getWriteTimeout()));
        } catch (IllegalArgumentException exception) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "rustfs configuration is incomplete", exception);
        }
    }
}
