package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import com.obs.services.BasicObsCredentialsProvider;
import com.obs.services.IObsCredentialsProvider;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;

import java.time.Duration;
import java.util.Objects;

/** 创建带有明确超时和凭证边界的 OBS 客户端。 */
public final class HuaweiObsClientFactory {
    /** OBS SDK 超时使用毫秒。 */
    private static final long MILLIS_PER_SECOND = 1_000L;

    /** 禁止创建客户端工厂。 */
    private HuaweiObsClientFactory() {
    }

    /** 根据静态配置创建 OBS 客户端。 */
    public static ObsClient create(HuaweiObsStorageConfiguration configuration) {
        IObsCredentialsProvider provider = staticProvider(configuration);
        return create(configuration, provider);
    }

    /** 根据应用提供的凭证 Provider 创建 OBS 客户端。 */
    public static ObsClient create(HuaweiObsStorageConfiguration configuration,
                                   IObsCredentialsProvider provider) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(provider, "provider");
        ObsConfiguration sdkConfiguration = sdkConfiguration(configuration);
        return new ObsClient(provider, sdkConfiguration);
    }

    /** 将配置中的长期或临时凭证转换为 SDK Provider。 */
    private static IObsCredentialsProvider staticProvider(
            HuaweiObsStorageConfiguration configuration) {
        if (configuration.credentialMode() == HuaweiObsCredentialMode.PROVIDER) {
            throw new IllegalArgumentException("provider credentials require an explicit provider");
        }
        if (configuration.credentialMode() == HuaweiObsCredentialMode.STS) {
            return new BasicObsCredentialsProvider(configuration.accessKey(),
                    configuration.secretKey(), configuration.securityToken());
        }
        return new BasicObsCredentialsProvider(configuration.accessKey(), configuration.secretKey());
    }

    /** 创建 OBS SDK 网络配置。 */
    private static ObsConfiguration sdkConfiguration(HuaweiObsStorageConfiguration configuration) {
        ObsConfiguration sdk = new ObsConfiguration();
        sdk.setEndPoint(configuration.endpoint());
        sdk.setConnectionTimeout(timeoutMillis(configuration.connectTimeout(), "connectTimeout"));
        sdk.setSocketTimeout(timeoutMillis(maxTimeout(configuration), "socketTimeout"));
        sdk.setCallTimeout(timeoutMillis(maxTimeout(configuration), "callTimeout"));
        return sdk;
    }

    /** 选择覆盖读写调用的最大超时时间。 */
    private static Duration maxTimeout(HuaweiObsStorageConfiguration configuration) {
        return max(configuration.readTimeout(), configuration.writeTimeout());
    }

    /** 返回两个超时时间中的较大值。 */
    private static Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    /** 将 Duration 安全转换为 SDK 要求的 int 毫秒。 */
    private static int timeoutMillis(Duration value, String name) {
        long millis = value.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must fit in positive integer milliseconds");
        }
        return Math.toIntExact(millis);
    }
}
