package io.github.lvdaxianer.dinghai.filestorage.spring;

/**
 * Starter 支持的文件存储后端类型。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public enum FileStorageProvider {
    /** 本地磁盘后端。 */
    LOCAL,
    /** MinIO 对象存储后端。 */
    MINIO,
    /** RustFS 对象存储后端。 */
    RUSTFS,
    /** 阿里云 OSS 对象存储后端。 */
    OSS
}
