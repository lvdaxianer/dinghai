package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.Set;

/**
 * 将 OSS SDK 失败转换为稳定且不泄密的错误契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
final class AliyunOssExceptionMapper {
    /** 向调用方公开的稳定失败消息。 */
    private static final String OPERATION_FAILED_MESSAGE = "OSS operation failed";
    private static final Set<String> AUTHENTICATION_CODES = Set.of(
            "AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch",
            "InvalidSecurityToken", "SecurityTokenExpired");
    private static final Set<String> MISSING_CODES = Set.of("NoSuchKey", "NoSuchObject", "NoSuchUpload");

    /**
     * 禁止实例化工具类。
     */
    private AliyunOssExceptionMapper() {
    }

    /**
     * 判断 OSS 服务端错误是否表示对象不存在。
     *
     * @param exception OSS 服务端异常
     * @return 对象缺失时返回 true
     */
    static boolean isMissing(Exception exception) {
        ServiceException serviceException = ServiceException.asCause(exception);
        return serviceException != null && MISSING_CODES.contains(serviceException.errorCode());
    }

    /**
     * 映射 SDK 错误，公共消息不携带 endpoint、凭据或 SDK 原始内容。
     *
     * @param exception SDK 或适配器异常
     * @return 稳定的文件存储异常
     */
    static FileStorageException map(Exception exception) {
        // 已归类的异常直接透传，避免丢失稳定错误码和最初原因。
        if (exception instanceof FileStorageException storageException) {
            return storageException;
        }
        return new FileStorageException(errorCode(exception), OPERATION_FAILED_MESSAGE, exception);
    }

    /**
     * 根据 OSS 服务端错误码归类公开错误码。
     *
     * @param exception SDK 或适配器异常
     * @return 与调用方恢复动作匹配的稳定错误码
     */
    private static FileStorageErrorCode errorCode(Exception exception) {
        // 认证或签名失败需要调用方修正凭据，其他失败按远端不可用处理。
        ServiceException serviceException = ServiceException.asCause(exception);
        if (serviceException != null && AUTHENTICATION_CODES.contains(serviceException.errorCode())) {
            return FileStorageErrorCode.AUTHENTICATION_FAILED;
        }
        return FileStorageErrorCode.STORAGE_UNAVAILABLE;
    }
}
