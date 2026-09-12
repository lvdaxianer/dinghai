package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import java.time.Instant;
import java.util.Objects;

/**
 * Web 上传成功后返回的已确认对象元数据与真实 key 预览地址。
 *
 * @param key 对象 key
 * @param contentLength 持久化后的内容长度
 * @param contentType 持久化后的内容类型
 * @param etag 持久化后的实体标签
 * @param lastModified 持久化后的最后修改时间
 * @param cacheControl 持久化后的缓存策略
 * @param previewUrl 真实 key 的根相对预览地址
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
public record WebUploadResult(ObjectKey key, long contentLength, String contentType, String etag,
                              Instant lastModified, CacheControl cacheControl, String previewUrl) {

    /**
     * 校验响应结果。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public WebUploadResult {
        key = Objects.requireNonNull(key, "key");
        contentType = Objects.requireNonNull(contentType, "contentType");
        etag = Objects.requireNonNull(etag, "etag");
        lastModified = Objects.requireNonNull(lastModified, "lastModified");
        cacheControl = Objects.requireNonNull(cacheControl, "cacheControl");
        previewUrl = Objects.requireNonNull(previewUrl, "previewUrl");
    }

    /**
     * 从 provider 返回的确认元数据创建 Web 响应。
     *
     * @param metadata provider 确认的对象元数据
     * @param previewUrl 真实 key 的根相对预览地址
     * @return Web 上传成功结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    static WebUploadResult from(ObjectMetadata metadata, String previewUrl) {
        return new WebUploadResult(metadata.key(), metadata.contentLength(), metadata.contentType(),
                metadata.etag(), metadata.lastModified(), metadata.cacheControl(), previewUrl);
    }
}
