package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Duration;
import java.util.Objects;

/** 创建 multipart 上传会话的请求。 */
public record MultipartUploadRequest(ObjectKey key, long totalSize, long partSize,
                                     String contentType, CacheControl cacheControl,
                                     Duration expiresIn) {

    /** 校验 multipart 会话参数。 */
    public MultipartUploadRequest {
        key = Objects.requireNonNull(key, "key");
        if (totalSize <= 0 || partSize <= 0 || partSize > totalSize) {
            throw new IllegalArgumentException("multipart size must be positive");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.trim();
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
        expiresIn = requireDuration(expiresIn);
    }

    /** 计算所需分片数量。 */
    public int partCount() {
        return Math.toIntExact((totalSize + partSize - 1) / partSize);
    }

    /** 校验有效期为正数。 */
    private static Duration requireDuration(Duration value) {
        Objects.requireNonNull(value, "expiresIn");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
        return value;
    }
}
