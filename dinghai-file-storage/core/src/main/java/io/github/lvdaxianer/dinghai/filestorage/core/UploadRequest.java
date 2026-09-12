package io.github.lvdaxianer.dinghai.filestorage.core;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

/**
 * 普通上传请求。
 *
 * @param key 对象 key
 * @param content 内容流，所有权转移给存储实现
 * @param contentLength 内容长度，未知时使用 -1
 * @param contentType 内容类型
 * @param cacheControl 缓存策略
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record UploadRequest(ObjectKey key, InputStream content, long contentLength,
                            String contentType, CacheControl cacheControl) {
    /** 默认最大上传大小（100 MB）。 */
    public static final long DEFAULT_MAX_SIZE_BYTES = 100L * 1024 * 1024;

    /** 校验上传请求字段。 */
    public UploadRequest {
        key = Objects.requireNonNull(key, "key");
        content = Objects.requireNonNull(content, "content");
        if (contentLength < -1) {
            throw new IllegalArgumentException("content length must be -1 or non-negative");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.trim();
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
    }

    /** 创建上传请求并使用默认缓存策略。 */
    public UploadRequest(ObjectKey key, InputStream content, long contentLength, String contentType) {
        this(key, content, contentLength, contentType, CacheControl.privateNoCache());
    }

    /** 创建使用默认缓存策略的上传请求。 */
    public static UploadRequest of(ObjectKey key, InputStream content, long contentLength,
                                   String contentType) {
        return new UploadRequest(key, content, contentLength, contentType,
                CacheControl.privateNoCache());
    }

    /** 按默认 100 MB 无类型白名单策略校验请求。 */
    public void validateDefault() {
        validate(DEFAULT_MAX_SIZE_BYTES, Set.of());
    }

    /** 按大小和内容类型策略校验请求。 */
    public void validate(long maxSizeBytes, Set<String> contentTypeWhitelist) {
        if (maxSizeBytes < 0) {
            throw new IllegalArgumentException("invalid upload max size");
        }
        if (contentLength > maxSizeBytes) {
            throw new IllegalArgumentException("upload exceeds maximum size");
        }
        if (contentTypeWhitelist != null && !contentTypeWhitelist.isEmpty()
                && !contentTypeWhitelist.contains(contentType)) {
            throw new IllegalArgumentException("content type is not allowed");
        }
    }
}
