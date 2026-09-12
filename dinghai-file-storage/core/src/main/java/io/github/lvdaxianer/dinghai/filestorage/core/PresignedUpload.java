package io.github.lvdaxianer.dinghai.filestorage.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 预签名上传结果。
 *
 * @param url 上传地址
 * @param method HTTP 方法
 * @param expiresAt 到期时间
 * @param requiredHeaders 必需请求头
 * @param formFields 表单字段
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record PresignedUpload(String url, String method, Instant expiresAt,
                              Map<String, String> requiredHeaders, Map<String, String> formFields) {
    /** 校验并防止调用方修改结果。 */
    public PresignedUpload {
        url = requireText(url, "url");
        method = requireText(method, "method");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        requiredHeaders = Map.copyOf(Objects.requireNonNull(requiredHeaders, "requiredHeaders"));
        formFields = Map.copyOf(Objects.requireNonNull(formFields, "formFields"));
    }

    /** 获取上传 URI。 */
    public String uri() {
        return url;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
