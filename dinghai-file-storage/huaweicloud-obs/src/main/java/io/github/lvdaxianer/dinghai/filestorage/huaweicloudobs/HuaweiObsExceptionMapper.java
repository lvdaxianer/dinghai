package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import com.obs.services.exception.ObsException;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;

import java.util.Set;

/** 将 OBS SDK 异常转换为不泄露敏感信息的稳定错误。 */
final class HuaweiObsExceptionMapper {
    /** OBS 缺失对象错误码。 */
    private static final Set<String> MISSING_OBJECT_CODES = Set.of("NoSuchKey", "NoSuchObject");
    /** OBS 缺失分片上传错误码。 */
    private static final Set<String> MISSING_UPLOAD_CODES = Set.of("NoSuchUpload");
    /** OBS 认证错误码。 */
    private static final Set<String> AUTHENTICATION_CODES = Set.of(
            "AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch",
            "InvalidSecurityToken", "SecurityTokenExpired");
    /** 对外稳定错误消息。 */
    private static final String OPERATION_FAILED_MESSAGE = "Huawei OBS operation failed";

    /** 禁止创建异常映射工具类。 */
    private HuaweiObsExceptionMapper() {
    }

    /** 判断异常是否表示对象不存在。 */
    static boolean isMissingObject(Exception exception) {
        return hasCode(exception, MISSING_OBJECT_CODES);
    }

    /** 判断异常是否表示 multipart 会话不存在。 */
    static boolean isMissingMultipart(Exception exception) {
        return hasCode(exception, MISSING_UPLOAD_CODES);
    }

    /** 将 SDK 异常转换为统一文件存储异常。 */
    static FileStorageException map(Exception exception) {
        if (exception instanceof FileStorageException storageException) {
            return storageException;
        }
        return new FileStorageException(errorCode(exception), OPERATION_FAILED_MESSAGE, exception);
    }

    /** 根据 OBS 错误码和 HTTP 状态推导稳定错误分类。 */
    static FileStorageErrorCode errorCode(Exception exception) {
        ObsException obsException = asObsException(exception);
        if (obsException == null) {
            return FileStorageErrorCode.STORAGE_UNAVAILABLE;
        }
        return errorCode(obsException);
    }

    /** 映射已识别的 OBS 异常。 */
    private static FileStorageErrorCode errorCode(ObsException exception) {
        String code = exception.getErrorCode();
        if (MISSING_OBJECT_CODES.contains(code)) {
            return FileStorageErrorCode.OBJECT_NOT_FOUND;
        }
        if (MISSING_UPLOAD_CODES.contains(code)) {
            return FileStorageErrorCode.MULTIPART_NOT_FOUND;
        }
        if (AUTHENTICATION_CODES.contains(code) || exception.getResponseCode() == 401
                || exception.getResponseCode() == 403) {
            return FileStorageErrorCode.AUTHENTICATION_FAILED;
        }
        if (exception.getResponseCode() == 409) {
            return FileStorageErrorCode.MULTIPART_CONFLICT;
        }
        return FileStorageErrorCode.STORAGE_UNAVAILABLE;
    }

    /** 判断异常的 OBS 错误码是否在目标集合中。 */
    private static boolean hasCode(Exception exception, Set<String> codes) {
        ObsException obsException = asObsException(exception);
        return obsException != null && codes.contains(obsException.getErrorCode());
    }

    /** 从异常链中提取 OBS 异常。 */
    private static ObsException asObsException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ObsException obsException) {
                return obsException;
            }
            current = current.getCause();
        }
        return null;
    }
}
