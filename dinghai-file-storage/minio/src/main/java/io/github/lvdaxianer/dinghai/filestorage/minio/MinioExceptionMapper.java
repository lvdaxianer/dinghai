package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.minio.errors.ErrorResponseException;

import java.util.Set;

/** 将 MinIO SDK 失败转换为稳定且不泄密的错误契约。 */
final class MinioExceptionMapper {
    private static final Set<String> AUTHENTICATION_CODES = Set.of(
            "AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch");
    private static final Set<String> MISSING_CODES = Set.of("NoSuchKey", "NoSuchObject");

    private MinioExceptionMapper() {
    }

    /** 判断服务端错误是否表示对象不存在。 */
    static boolean isMissing(ErrorResponseException exception) {
        return MISSING_CODES.contains(exception.errorResponse().code());
    }

    /** 映射 SDK 错误，公共消息不携带 endpoint、凭据或 SDK 原始内容。 */
    static FileStorageException map(Exception exception) {
        return new FileStorageException(errorCode(exception), "MinIO operation failed", exception);
    }

    private static FileStorageErrorCode errorCode(Exception exception) {
        if (exception instanceof ErrorResponseException response
                && AUTHENTICATION_CODES.contains(response.errorResponse().code())) {
            return FileStorageErrorCode.AUTHENTICATION_FAILED;
        }
        return FileStorageErrorCode.STORAGE_UNAVAILABLE;
    }

}
