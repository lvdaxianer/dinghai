package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.spring;

import com.obs.services.ObsClient;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageAutoConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageProviderFactory;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 华为云 OBS 专用 Starter 自动配置测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public class HuaweiObsFileStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class,
                    HuaweiObsFileStorageAutoConfiguration.class));

    /** 选中 OBS 时应使用宿主提供的客户端创建匹配 factory。 */
    @Test
    public void shouldCreateHuaweiObsProviderFactoryWhenSelected() {
        contextRunner.withBean(ObsClient.class, () -> mock(ObsClient.class))
                .withPropertyValues(properties("huaweicloud-obs"))
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProviderFactory.class);
                    assertThat(context).hasSingleBean(FileStorage.class);
                });
    }

    /** 未选中 OBS 时不得注册 OBS factory。 */
    @Test
    public void shouldNotCreateHuaweiObsProviderFactoryWhenAnotherProviderIsSelected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HuaweiObsFileStorageAutoConfiguration.class))
                .withPropertyValues("dinghai.file-storage.provider=local")
                .run(context -> assertThat(context).doesNotHaveBean(FileStorageProviderFactory.class));
    }

    private String[] properties(String provider) {
        return new String[]{
                "dinghai.file-storage.provider=" + provider,
                "dinghai.file-storage.bucket=test-bucket",
                "dinghai.file-storage.huaweicloud-obs.endpoint=https://obs.cn-north-4.myhuaweicloud.com",
                "dinghai.file-storage.huaweicloud-obs.region=cn-north-4",
                "dinghai.file-storage.huaweicloud-obs.access-key=test-access-key",
                "dinghai.file-storage.huaweicloud-obs.secret-key=test-secret-key",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b"
        };
    }
}
