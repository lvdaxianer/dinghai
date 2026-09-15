package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.time.Duration;

/**
 * 创建 multipart 会话的 HTTP 请求体。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record MultipartInitiateRequest(String key, long size, long partSize, String contentType,
                                       String cacheControl, Duration expiresIn) {

    /** 将 HTTP 请求转换为经过核心契约校验的会话请求。 */
    MultipartUploadRequest toCoreRequest() {
        return new MultipartUploadRequest(ObjectKey.of(key), size, partSize, contentType,
                cacheControl == null || cacheControl.isBlank() ? CacheControl.privateNoCache()
                        : new CacheControl(cacheControl), expiresIn);
    }
}
