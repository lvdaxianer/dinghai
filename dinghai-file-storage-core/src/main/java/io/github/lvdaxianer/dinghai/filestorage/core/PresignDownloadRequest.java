package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Duration;
import java.util.Objects;

/**
 * 预签名下载请求。
 *
 * @param key 对象 key
 * @param expiresIn 有效期
 * @param disposition 响应模式
 * @param cacheControl 缓存策略
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record PresignDownloadRequest(ObjectKey key, Duration expiresIn,
                                     ResponseDisposition disposition, CacheControl cacheControl) {
    /** 响应内容处置模式。 */
    public enum ResponseDisposition { INLINE, ATTACHMENT }

    /** 创建并校验预签名下载请求。 */
    public PresignDownloadRequest {
        key = Objects.requireNonNull(key, "key");
        expiresIn = Objects.requireNonNull(expiresIn, "expiresIn");
        if (expiresIn.isNegative() || expiresIn.isZero()) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
        disposition = disposition == null ? ResponseDisposition.ATTACHMENT : disposition;
        cacheControl = cacheControl == null ? CacheControl.privateNoCache() : cacheControl;
    }

    /** 创建预签名下载请求并使用默认缓存策略。 */
    public PresignDownloadRequest(ObjectKey key, Duration expiresIn,
                                  ResponseDisposition disposition) {
        this(key, expiresIn, disposition, CacheControl.privateNoCache());
    }
}
