package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 创建具有明确网络超时的 RustFS AWS S3 client 与 presigner。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public final class RustfsClientFactory {

    /** RustFS S3 API 使用固定的签名地域。 */
    private static final Region SIGNING_REGION = Region.US_EAST_1;

    /** 私有工具类构造器。 */
    private RustfsClientFactory() {
    }

    /**
     * 从已校验配置创建独立 AWS S3 客户端，不执行 bucket 创建。
     *
     * @param configuration 已校验连接配置
     * @return RustFS S3 client 与 presigner
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    public static RustfsClients create(RustfsStorageConfiguration configuration) {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(configuration.accessKey(), configuration.secretKey()));
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .connectionTimeout(configuration.connectTimeout())
                .socketTimeout(maxTimeout(configuration.readTimeout(), configuration.writeTimeout()))
                .build();
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        S3Client client = S3Client.builder()
                .httpClient(httpClient)
                .endpointOverride(URI.create(configuration.endpoint()))
                .region(SIGNING_REGION)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(maxTimeout(configuration.readTimeout(), configuration.writeTimeout()))
                        .apiCallAttemptTimeout(maxTimeout(configuration.readTimeout(), configuration.writeTimeout())))
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(configuration.endpoint()))
                .region(SIGNING_REGION)
                .credentialsProvider(credentials)
                .build();
        return new RustfsClients(client, presigner);
    }

    private static Duration maxTimeout(Duration readTimeout, Duration writeTimeout) {
        return readTimeout.compareTo(writeTimeout) >= 0 ? readTimeout : writeTimeout;
    }
}
