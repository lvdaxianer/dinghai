package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;

/**
 * 创建具有明确网络超时的 RustFS S3 客户端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
public final class RustfsClientFactory {

    /** 私有工具类构造器。 */
    private RustfsClientFactory() {
    }

    /**
     * 从已校验配置创建 RustFS S3 客户端，不执行 bucket 创建。
     *
     * @param configuration 已校验连接配置
     * @return RustFS S3 客户端
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public static MinioClient create(RustfsStorageConfiguration configuration) {
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
