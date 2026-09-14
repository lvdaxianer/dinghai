package io.github.lvdaxianer.dinghai.filestorage.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import java.time.Duration;

/**
 * 创建具有明确超时限制的阿里云 OSS 客户端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public final class OssClientFactory {

    /**
     * 禁止实例化工具类。
     */
    private OssClientFactory() {
    }

    /**
     * 从已校验配置创建 OSS 客户端，不执行 bucket 创建或配置。
     *
     * @param configuration 已校验的 OSS 连接配置
     * @return 具有明确网络超时的 OSS SDK 客户端
     */
    public static OSS create(OssStorageConfiguration configuration) {
        ClientBuilderConfiguration clientConfiguration = new ClientBuilderConfiguration();
        clientConfiguration.setConnectionTimeout(millis(configuration.connectTimeout(),
                "connectTimeout"));
        clientConfiguration.setSocketTimeout(millis(configuration.readTimeout(), "readTimeout"));
        clientConfiguration.setRequestTimeoutEnabled(true);
        clientConfiguration.setRequestTimeout(millis(configuration.writeTimeout(), "writeTimeout"));
        return new OSSClientBuilder().build(configuration.endpoint(), configuration.accessKey(),
                configuration.secretKey(), clientConfiguration);
    }

    /**
     * 将正超时转换为 OSS SDK 所需的毫秒整数。
     *
     * @param timeout 经配置读取的超时
     * @param name 配置项名称
     * @return 可传递给 OSS SDK 的毫秒数
     */
    private static int millis(Duration timeout, String name) {
        long millis = timeout.toMillis();
        // SDK 使用 int 毫秒；溢出会使超时语义不可靠。
        if (millis <= 0L || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between 1ms and "
                    + Integer.MAX_VALUE + "ms");
        }
        return (int) millis;
    }
}
