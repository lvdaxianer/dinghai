package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import java.time.Duration;
import java.util.Objects;

/**
 * 阿里云 OSS 固定 bucket 连接配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public record AliyunOssStorageConfiguration(String region, String bucket,
                                      Duration connectTimeout,
                                      Duration readTimeout, Duration writeTimeout) {
    /** 默认网络超时。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 创建并校验 OSS 连接配置。
     */
    public AliyunOssStorageConfiguration {
        region = requireText(region, "region");
        bucket = requireText(bucket, "bucket");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        writeTimeout = requirePositive(writeTimeout, "writeTimeout");
    }

    /**
     * 使用默认网络超时创建配置。
     *
     * @param region OSS 地域 ID
     * @param bucket 已预先创建的 bucket
     */
    public AliyunOssStorageConfiguration(String region, String bucket) {
        this(region, bucket, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT,
                DEFAULT_TIMEOUT);
    }

    /**
     * 校验并标准化必填文本配置。
     *
     * @param value 原始配置值
     * @param name 配置项名称
     * @return 去除首尾空白后的配置值
     */
    private static String requireText(String value, String name) {
        // 空白配置无法用于安全地建立远端连接，必须在启动阶段失败。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 校验网络超时为正值。
     *
     * @param value 原始超时值
     * @param name 配置项名称
     * @return 经校验的超时值
     */
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        // 零或负值会取消调用上限，可能耗尽请求线程。
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
