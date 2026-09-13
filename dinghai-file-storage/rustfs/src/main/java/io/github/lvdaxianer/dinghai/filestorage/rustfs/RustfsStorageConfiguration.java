package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import java.time.Duration;
import java.util.Objects;

/**
 * RustFS 固定 bucket 连接配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
public record RustfsStorageConfiguration(String endpoint, String bucket, String accessKey,
                                        String secretKey, Duration connectTimeout,
                                        Duration readTimeout, Duration writeTimeout) {
    /** 默认网络超时。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 创建并校验 RustFS 连接配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public RustfsStorageConfiguration {
        endpoint = requireText(endpoint, "endpoint");
        bucket = requireText(bucket, "bucket");
        accessKey = requireText(accessKey, "accessKey");
        secretKey = requireText(secretKey, "secretKey");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        writeTimeout = requirePositive(writeTimeout, "writeTimeout");
    }

    /**
     * 使用默认网络超时创建配置。
     *
     * @param endpoint RustFS API 地址
     * @param bucket 私有对象桶
     * @param accessKey 访问密钥
     * @param secretKey 私密访问密钥
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public RustfsStorageConfiguration(String endpoint, String bucket, String accessKey,
                                      String secretKey) {
        this(endpoint, bucket, accessKey, secretKey, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT,
                DEFAULT_TIMEOUT);
    }

    /**
     * 校验并规整必填文本配置。
     *
     * @param value 待校验文本
     * @param name 配置名称
     * @return 去除首尾空白后的文本
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 校验网络超时为正数。
     *
     * @param value 超时值
     * @param name 配置名称
     * @return 经校验的超时值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
