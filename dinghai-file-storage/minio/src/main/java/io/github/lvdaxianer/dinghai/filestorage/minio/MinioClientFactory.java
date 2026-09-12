package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;

/** 创建具有明确网络超时的 MinIO 客户端。 */
public final class MinioClientFactory {
    private MinioClientFactory() {
    }

    /** 从已校验配置创建 MinIO 客户端，不执行 bucket 创建。 */
    public static MinioClient create(MinioStorageConfiguration configuration) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(configuration.connectTimeout())
                .readTimeout(configuration.readTimeout())
                .writeTimeout(configuration.writeTimeout())
                .build();
        return MinioClient.builder().endpoint(configuration.endpoint())
                .credentials(configuration.accessKey(), configuration.secretKey())
                .httpClient(httpClient).build();
    }
}
