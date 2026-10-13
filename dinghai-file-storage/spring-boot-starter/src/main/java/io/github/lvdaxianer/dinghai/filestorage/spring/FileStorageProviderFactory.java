package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;

/**
 * 按 provider 创建文件存储适配器的公共扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public interface FileStorageProviderFactory {

    /**
     * 返回该 factory 支持的 provider。
     *
     * @return provider 类型
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    FileStorageProvider provider();

    /**
     * 使用已校验的公共配置创建存储适配器。
     *
     * @param properties 文件存储配置
     * @param bucket 已校验的公共 bucket
     * @return 文件存储适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    FileStorage create(FileStorageProperties properties, String bucket);
}
