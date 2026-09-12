package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Duration;
import java.util.Objects;

/**
 * 预签名上传请求。
 *
 * @param key 对象 key
 * @param expiresIn 有效期
 * @param contentType 允许的内容类型
 * @param maxSizeBytes 允许的最大内容长度
 * @param cacheControl 上传对象缓存策略
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record PresignUploadRequest(ObjectKey key, Duration expiresIn, String contentType,
                                   long maxSizeBytes, CacheControl cacheControl) {
    /** 创建并校验预签名上传请求。 */
    public PresignUploadRequest {
        key = Objects.requireNonNull(key, "key");
        expiresIn = requireDuration(expiresIn);
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.trim();
        if (maxSizeBytes < 0) {
            throw new IllegalArgumentException("invalid upload max size");
        }
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
    }

    /** 创建预签名上传请求并使用默认缓存策略。 */
    public PresignUploadRequest(ObjectKey key, Duration expiresIn, String contentType,
                                long maxSizeBytes) {
        this(key, expiresIn, contentType, maxSizeBytes, CacheControl.privateNoCache());
    }

    private static Duration requireDuration(Duration duration) {
        Objects.requireNonNull(duration, "expiresIn");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
        return duration;
    }
}
