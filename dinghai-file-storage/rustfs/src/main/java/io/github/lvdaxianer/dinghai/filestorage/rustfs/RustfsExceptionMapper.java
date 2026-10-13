package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

    /** 将 AWS SDK 或网络异常转换为不泄漏供应商细节的统一异常。 */
    static FileStorageException map(Throwable exception) {
        if (exception instanceof FileStorageException fileStorageException) {
            return map(fileStorageException);
        }
        if (exception instanceof S3Exception s3Exception) {
            String code = s3Exception.awsErrorDetails() == null
                    ? null : s3Exception.awsErrorDetails().errorCode();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return new FileStorageException(FileStorageErrorCode.OBJECT_NOT_FOUND,
                        "RustFS operation failed", exception);
            }
            if ("NoSuchUpload".equals(code)) {
                return new FileStorageException(FileStorageErrorCode.MULTIPART_NOT_FOUND,
                        "RustFS operation failed", exception);
            }
            if ("InvalidPart".equals(code) || "InvalidPartOrder".equals(code)) {
                return new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                        "RustFS operation failed", exception);
            }
            if (s3Exception.statusCode() == 401 || s3Exception.statusCode() == 403
                    || "InvalidAccessKeyId".equals(code) || "SignatureDoesNotMatch".equals(code)) {
                return new FileStorageException(FileStorageErrorCode.AUTHENTICATION_FAILED,
                        "RustFS operation failed", exception);
            }
        }
        return new FileStorageException(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                "RustFS operation failed", exception);
    }
}
