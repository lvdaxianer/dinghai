package io.github.lvdaxianer.dinghai.filestorage.minio.spring;

import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageAutoConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinIO 专用 Starter 自动配置测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public class MinioFileStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class,
                    MinioFileStorageAutoConfiguration.class));

    /** 选中 MinIO 时应注册匹配 factory 和唯一文件存储。 */
    @Test
    public void shouldCreateMinioProviderFactoryWhenSelected() {
        contextRunner.withPropertyValues(properties("minio",
                        "dinghai.file-storage.minio.endpoint=http://localhost:9000",
                        "dinghai.file-storage.minio.access-key=test-access-key",
                        "dinghai.file-storage.minio.secret-key=test-secret-key"))
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProviderFactory.class);
                    assertThat(context).hasSingleBean(io.github.lvdaxianer.dinghai.filestorage.core.FileStorage.class);
                });
    }

    /** 未选中 MinIO 时不得注册 MinIO factory。 */
    @Test
    public void shouldNotCreateMinioProviderFactoryWhenAnotherProviderIsSelected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MinioFileStorageAutoConfiguration.class))
                .withPropertyValues("dinghai.file-storage.provider=local")
                .run(context -> assertThat(context).doesNotHaveBean(FileStorageProviderFactory.class));
    }

    private String[] properties(String provider, String... providerProperties) {
        String[] values = new String[providerProperties.length + 4];
        values[0] = "dinghai.file-storage.provider=" + provider;
        values[1] = "dinghai.file-storage.bucket=test-bucket";
        values[2] = "dinghai.file-storage.storage-alias=documents";
        values[3] = "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b";
        System.arraycopy(providerProperties, 0, values, 4, providerProperties.length);
        return values;
    }
}
