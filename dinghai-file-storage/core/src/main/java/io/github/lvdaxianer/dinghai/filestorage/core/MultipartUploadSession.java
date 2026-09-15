package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** multipart 上传会话及其已确认分片快照。 */
public record MultipartUploadSession(String uploadId, ObjectKey key, long totalSize,
                                     long partSize, String contentType,
                                     CacheControl cacheControl, int partCount,
                                     Instant expiresAt, List<MultipartPart> parts) {

    /** 从创建请求构造空分片会话。 */
    public MultipartUploadSession(String uploadId, MultipartUploadRequest request,
                                   Instant expiresAt) {
        this(uploadId, request.key(), request.totalSize(), request.partSize(),
                request.contentType(), request.cacheControl(), request.partCount(),
                Objects.requireNonNull(expiresAt, "expiresAt"), List.of());
    }

    /** 校验并冻结会话字段。 */
    public MultipartUploadSession {
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("uploadId must not be blank");
        }
        key = Objects.requireNonNull(key, "key");
        if (totalSize <= 0 || partSize <= 0 || partCount <= 0) {
            throw new IllegalArgumentException("multipart session sizes must be positive");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.trim();
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
    }

    /** 返回带已上传分片快照的新会话值。 */
    public MultipartUploadSession withParts(List<MultipartPart> uploadedParts) {
        return new MultipartUploadSession(uploadId, key, totalSize, partSize, contentType,
                cacheControl, partCount, expiresAt, uploadedParts);
    }
}
