package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import java.time.Duration;

/**
 * 创建具有明确超时限制的阿里云 OSS 客户端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public final class AliyunOssClientFactory {

    /** AccessKey 环境变量名称。 */
    public static final String ACCESS_KEY_ID_ENVIRONMENT = "OSS_ACCESS_KEY_ID";
    /** AccessKeySecret 环境变量名称。 */
    public static final String ACCESS_KEY_SECRET_ENVIRONMENT = "OSS_ACCESS_KEY_SECRET";

    /**
     * 禁止实例化工具类。
     */
    private AliyunOssClientFactory() {
    }

    /**
     * 从已校验配置创建 OSS 客户端，不执行 bucket 创建或配置。
     *
     * @param configuration 已校验的 OSS 连接配置
     * @return 具有明确网络超时的 OSS SDK 客户端
     */
    public static OSSClient create(AliyunOssStorageConfiguration configuration) {
        return OSSClient.newBuilder().region(configuration.region())
                .credentialsProvider(new StaticCredentialsProvider(requiredEnvironment(
                        ACCESS_KEY_ID_ENVIRONMENT), requiredEnvironment(ACCESS_KEY_SECRET_ENVIRONMENT)))
                .connectTimeout(configuration.connectTimeout())
                .readWriteTimeout(ioTimeout(configuration.readTimeout(), configuration.writeTimeout()))
                .build();
    }

    /**
     * 读取并校验必填的部署环境变量。
     *
     * @param name 环境变量名称
     * @return 去除首尾空白后的变量值
     */
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        // 凭证缺失必须在远程调用前失败，且不回显任何凭证内容。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    /**
     * 将 V2 的统一读写超时限制为配置的较短一侧。
     *
     * @param readTimeout 读取超时
     * @param writeTimeout 写入超时
     * @return 不超过任一方向配置的 I/O 超时
     */
    private static Duration ioTimeout(Duration readTimeout, Duration writeTimeout) {
        return readTimeout.compareTo(writeTimeout) <= 0 ? readTimeout : writeTimeout;
    }
}
