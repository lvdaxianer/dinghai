package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 预签名下载结果。
 *
 * @param url 下载地址
 * @param method HTTP 方法
 * @param expiresAt 到期时间
 * @param disposition 响应模式
 * @param requiredHeaders 必需请求头
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record PresignedDownload(String url, String method, Instant expiresAt,
                                PresignDownloadRequest.ResponseDisposition disposition,
                                Map<String, String> requiredHeaders) {
    /** 校验并防止调用方修改结果。 */
    public PresignedDownload {
        if (url == null || url.isBlank() || method == null || method.isBlank()) {
            throw new IllegalArgumentException("url and method must not be blank");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        disposition = Objects.requireNonNull(disposition, "disposition");
        requiredHeaders = Map.copyOf(Objects.requireNonNull(requiredHeaders, "requiredHeaders"));
    }

    /** 获取下载 URI。 */
    public String uri() {
        return url;
    }
}
