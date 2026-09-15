package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将文件存储稳定错误码映射为不泄露内部细节的 HTTP 响应。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@RestControllerAdvice
public class FileStorageExceptionHandler {

    /**
     * 处理文件存储异常。
     *
     * @param exception 文件存储异常
     * @return 稳定的 HTTP 错误响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Map<String, String>> handleFileStorageException(
            FileStorageException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status(exception.errorCode()));
        if (exception.errorCode() == FileStorageErrorCode.RATE_LIMITED) {
            response.header("Retry-After", "1");
        }
        response.contentType(MediaType.APPLICATION_JSON);
        return response
                .body(Map.of("code", exception.errorCode().name(), "message", exception.getMessage()));
    }

    /**
     * 将错误码映射为客户端可处理的 HTTP 状态。
     *
     * @param errorCode 稳定文件存储错误码
     * @return 对应 HTTP 状态
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private HttpStatus status(FileStorageErrorCode errorCode) {
        if (errorCode == FileStorageErrorCode.OBJECT_NOT_FOUND
                || errorCode == FileStorageErrorCode.MULTIPART_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode == FileStorageErrorCode.MULTIPART_EXPIRED) {
            return HttpStatus.GONE;
        }
        if (errorCode == FileStorageErrorCode.MULTIPART_CONFLICT) {
            return HttpStatus.CONFLICT;
        }
        if (errorCode == FileStorageErrorCode.OBJECT_TOO_LARGE) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (errorCode == FileStorageErrorCode.INVALID_PRESIGN
                || errorCode == FileStorageErrorCode.AUTHENTICATION_FAILED) {
            return HttpStatus.FORBIDDEN;
        }
        if (errorCode == FileStorageErrorCode.STORAGE_UNAVAILABLE) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (errorCode == FileStorageErrorCode.RATE_LIMITED) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
