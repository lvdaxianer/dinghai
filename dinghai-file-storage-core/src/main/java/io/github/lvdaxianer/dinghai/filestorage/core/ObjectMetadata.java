package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Instant;
import java.util.Objects;

/**
 * 对象统一元数据。
 *
 * @param key 对象 key
 * @param contentLength 内容长度
 * @param contentType 内容类型
 * @param etag 内容实体标签
 * @param lastModified 最后修改时间
 * @param cacheControl 缓存策略
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record ObjectMetadata(ObjectKey key, long contentLength, String contentType,
                             String etag, Instant lastModified, CacheControl cacheControl) {
    /** 校验对象元数据。 */
    public ObjectMetadata {
        key = Objects.requireNonNull(key, "key");
        if (contentLength < 0) {
            throw new IllegalArgumentException("content length must be non-negative");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.trim();
        etag = Objects.requireNonNull(etag, "etag");
        lastModified = Objects.requireNonNull(lastModified, "lastModified");
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
    }

    /** 创建使用默认缓存策略的对象元数据。 */
    public ObjectMetadata(ObjectKey key, long contentLength, String contentType,
                          String etag, Instant lastModified) {
        this(key, contentLength, contentType, etag, lastModified, CacheControl.privateNoCache());
    }
}
