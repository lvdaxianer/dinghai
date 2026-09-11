package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 文件存储稳定异常，不在公共消息中暴露后端敏感细节。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageException extends RuntimeException {
    private static final int MAX_PUBLIC_MESSAGE_LENGTH = 256;
    private static final Pattern URI = Pattern.compile("(?i)\\b(?:https?|s3|gs|file)://\\S+");
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![A-Za-z0-9])/(?:[^\\s/]+/)+[^\\s]*");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)[A-Za-z]:\\\\[^\\s]*");
    private static final Pattern SECRET_PARAMETER = Pattern.compile(
            "(?i)((?:access[-_]?key|secret[-_]?key|token|password|signature)=)[^&\\s]+");
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
        String sanitized = SECRET_PARAMETER.matcher(message).replaceAll("$1[REDACTED]");
        sanitized = URI.matcher(sanitized).replaceAll("[REDACTED_URI]");
        sanitized = WINDOWS_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
        sanitized = UNIX_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
        if (sanitized.length() > MAX_PUBLIC_MESSAGE_LENGTH) {
            return sanitized.substring(0, MAX_PUBLIC_MESSAGE_LENGTH);
        }
        return sanitized;
    }
}
