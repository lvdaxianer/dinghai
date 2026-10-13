package io.github.lvdaxianer.dinghai.filestorage.rustfs.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageAutoConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RustFS 专用 Starter 自动配置测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public class RustfsFileStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class,
                    RustfsFileStorageAutoConfiguration.class));

    /** 选中 RustFS 时应注册匹配 factory 和唯一文件存储。 */
    @Test
    public void shouldCreateRustfsProviderFactoryWhenSelected() {
        contextRunner.withPropertyValues(properties("rustfs",
                        "dinghai.file-storage.rustfs.endpoint=http://localhost:9100",
                        "dinghai.file-storage.rustfs.access-key=test-access-key",
                        "dinghai.file-storage.rustfs.secret-key=test-secret-key"))
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProviderFactory.class);
                    assertThat(context).hasSingleBean(FileStorage.class);
                });
    }

    /** 未选中 RustFS 时不得注册 RustFS factory。 */
    @Test
    public void shouldNotCreateRustfsProviderFactoryWhenAnotherProviderIsSelected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RustfsFileStorageAutoConfiguration.class))
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
