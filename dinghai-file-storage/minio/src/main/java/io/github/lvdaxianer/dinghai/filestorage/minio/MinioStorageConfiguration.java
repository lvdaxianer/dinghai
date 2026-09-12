package io.github.lvdaxianer.dinghai.filestorage.minio;

import java.time.Duration;
import java.util.Objects;

/** MinIO 固定 bucket 连接配置。 */
public record MinioStorageConfiguration(String endpoint, String bucket, String accessKey,
                                       String secretKey, Duration connectTimeout,
                                       Duration readTimeout, Duration writeTimeout) {
    /** 默认网络超时为十秒。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** 创建并校验 MinIO 连接配置。 */
    public MinioStorageConfiguration {
        endpoint = requireText(endpoint, "endpoint");
        bucket = requireText(bucket, "bucket");
        accessKey = requireText(accessKey, "accessKey");
        secretKey = requireText(secretKey, "secretKey");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        writeTimeout = requirePositive(writeTimeout, "writeTimeout");
    }

    /** 使用默认网络超时创建配置。 */
    public MinioStorageConfiguration(String endpoint, String bucket, String accessKey,
                                     String secretKey) {
        this(endpoint, bucket, accessKey, secretKey, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT,
                DEFAULT_TIMEOUT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
