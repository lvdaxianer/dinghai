package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import java.time.Duration;
import java.util.Objects;

/** 华为云 OBS 固定 bucket 连接配置。 */
public record HuaweiObsStorageConfiguration(String endpoint, String region, String bucket,
                                            HuaweiObsCredentialMode credentialMode,
                                            String accessKey, String secretKey,
                                            String securityToken, Duration connectTimeout,
                                            Duration readTimeout, Duration writeTimeout) {
    /** 默认网络超时时间。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** 创建并校验 OBS 连接配置。 */
    public HuaweiObsStorageConfiguration {
        endpoint = requireText(endpoint, "endpoint");
        region = requireText(region, "region");
        bucket = requireText(bucket, "bucket");
        credentialMode = Objects.requireNonNull(credentialMode, "credentialMode");
        accessKey = optionalText(accessKey);
        secretKey = optionalText(secretKey);
        securityToken = optionalText(securityToken);
        validateCredentials(credentialMode, accessKey, secretKey, securityToken);
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        writeTimeout = requirePositive(writeTimeout, "writeTimeout");
    }

    /** 创建长期 AK/SK 配置。 */
    public static HuaweiObsStorageConfiguration staticCredentials(
            String endpoint, String region, String bucket, String accessKey, String secretKey) {
        return new HuaweiObsStorageConfiguration(endpoint, region, bucket,
                HuaweiObsCredentialMode.STATIC, accessKey, secretKey, null,
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
    }

    /** 创建 STS 临时凭证配置。 */
    public static HuaweiObsStorageConfiguration stsCredentials(
            String endpoint, String region, String bucket, String accessKey,
            String secretKey, String securityToken) {
        return new HuaweiObsStorageConfiguration(endpoint, region, bucket,
                HuaweiObsCredentialMode.STS, accessKey, secretKey, securityToken,
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
    }

    /** 校验凭证字段与来源模式一致。 */
    private static void validateCredentials(HuaweiObsCredentialMode mode, String accessKey,
                                            String secretKey, String securityToken) {
        if (mode == HuaweiObsCredentialMode.PROVIDER) {
            return;
        }
        requireText(accessKey, "accessKey");
        requireText(secretKey, "secretKey");
        if (mode == HuaweiObsCredentialMode.STS) {
            requireText(securityToken, "securityToken");
        } else if (securityToken != null) {
            throw new IllegalArgumentException("securityToken is only valid for STS credentials");
        }
    }

    /** 将可选配置文本标准化为空值。 */
    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 校验必填文本配置。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 校验网络超时为正数。 */
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
