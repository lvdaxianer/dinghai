package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;

/**
 * 将底层 S3 适配失败转换为 RustFS 的稳定公开错误。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
final class RustfsExceptionMapper {

    /** 私有工具类构造器。 */
    private RustfsExceptionMapper() {
    }

    /**
     * 保留稳定错误码并隐藏底层供应商名称和细节。
     *
     * @param exception 底层统一存储异常
     * @return RustFS 命名的安全异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    static FileStorageException map(FileStorageException exception) {
        return new FileStorageException(exception.errorCode(), "RustFS operation failed", exception);
    }
}
