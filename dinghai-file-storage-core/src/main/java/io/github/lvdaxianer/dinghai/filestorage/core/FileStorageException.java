package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;

/**
 * 文件存储稳定异常，不在公共消息中暴露后端敏感细节。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageException extends RuntimeException {
    private final FileStorageErrorCode errorCode;

    /** 创建带稳定错误码和安全消息的异常。 */
    public FileStorageException(FileStorageErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /** 创建带稳定错误码、安全消息和底层原因的异常。 */
    public FileStorageException(FileStorageErrorCode errorCode, String message, Throwable cause) {
        super(safeMessage(message), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /** 获取稳定错误码。 */
    public FileStorageErrorCode errorCode() {
        return errorCode;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "file storage operation failed";
        }
        if (message.indexOf('\r') >= 0 || message.indexOf('\n') >= 0) {
            return "file storage operation failed";
        }
        return message;
    }
}
