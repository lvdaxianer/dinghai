package io.github.lvdaxianer.dinghai.filestorage.aliyunoss.spring;

import com.aliyun.sdk.service.oss2.OSSClient;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageAutoConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 阿里云 OSS 专用 Starter 自动配置测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public class AliyunOssFileStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class,
                    AliyunOssFileStorageAutoConfiguration.class));

    /** 选中 OSS 时应使用宿主提供的客户端创建匹配 factory。 */
    @Test
    public void shouldCreateAliyunOssProviderFactoryWhenSelected() {
        contextRunner.withBean(OSSClient.class, () -> mock(OSSClient.class))
                .withPropertyValues(properties("aliyun-oss"))
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProviderFactory.class);
                    assertThat(context).hasSingleBean(FileStorage.class);
                });
    }

    /** 未选中 OSS 时不得注册 OSS factory。 */
    @Test
    public void shouldNotCreateAliyunOssProviderFactoryWhenAnotherProviderIsSelected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AliyunOssFileStorageAutoConfiguration.class))
                .withPropertyValues("dinghai.file-storage.provider=local")
                .run(context -> assertThat(context).doesNotHaveBean(FileStorageProviderFactory.class));
    }

    private String[] properties(String provider) {
        return new String[]{
                "dinghai.file-storage.provider=" + provider,
                "dinghai.file-storage.bucket=test-bucket",
                "dinghai.file-storage.aliyun-oss.region=oss-cn-beijing",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b"
        };
    }
}
