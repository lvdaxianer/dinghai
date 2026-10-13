package io.github.lvdaxianer.dinghai.filestorage.rustfs.spring;

import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * RustFS 文件存储专用自动配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
@AutoConfiguration
@ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "rustfs")
public class RustfsFileStorageAutoConfiguration {

    /** 注册 RustFS provider factory。 */
    @Bean
    @ConditionalOnMissingBean(name = "rustfsFileStorageProviderFactory")
    public FileStorageProviderFactory rustfsFileStorageProviderFactory() {
        return new RustfsFileStorageProviderFactory();
    }
}
