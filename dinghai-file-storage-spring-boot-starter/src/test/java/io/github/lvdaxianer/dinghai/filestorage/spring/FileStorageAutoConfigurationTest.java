package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件存储自动装配测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageAutoConfigurationTest {

    /** 本地测试存储目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 自动装配测试上下文。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class));

    /**
     * local provider 应仅注册本地存储主 Bean。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRegisterOnlyLocalFileStorageForLocalProvider() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context).hasSingleBean(LocalFileStorageAdapter.class);
                    assertThat(context).doesNotHaveBean(MinioFileStorageAdapter.class);
                });
    }

    /**
     * minio provider 应仅注册 MinIO 存储主 Bean。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRegisterOnlyMinioFileStorageForMinioProvider() {
        contextRunner.withPropertyValues(minioProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context).hasSingleBean(MinioFileStorageAdapter.class);
                    assertThat(context).doesNotHaveBean(LocalFileStorageAdapter.class);
                });
    }

    /**
     * 未知 provider 必须在容器启动时失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastForUnknownProvider() {
        contextRunner.withPropertyValues("dinghai.file-storage.provider=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 被选中的 local provider 缺少根目录时必须失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastWhenSelectedLocalProviderIsIncomplete() {
        contextRunner.withPropertyValues(
                        "dinghai.file-storage.provider=local",
                        "dinghai.file-storage.security.signing-key=test-signing-key")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 生产环境 local provider 缺少签名密钥时必须失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastWhenProductionLocalProviderHasNoSigningKey() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "dinghai.file-storage.provider=local",
                        "dinghai.file-storage.local.root=" + storageRoot())
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * Web 直接访问必须默认关闭。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldDisableDirectAccessByDefault() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    FileStorageProperties properties = context.getBean(FileStorageProperties.class);
                    assertThat(properties.getWeb().isDirectAccess()).isFalse();
                    assertThat(properties.getWeb().getPathPrefix()).isEqualTo("/files");
                    assertThat(properties.getWeb().isEnabled()).isTrue();
                });
    }

    /**
     * 非法 Web 路径前缀必须在启动时被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFailFastForInvalidWebPathPrefix() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.path-prefix=files?token=value")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 返回 local provider 所需的完整属性。
     *
     * @return 本地 provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String[] localProperties() {
        return new String[]{
                "dinghai.file-storage.provider=local",
                "dinghai.file-storage.local.root=" + storageRoot(),
                "dinghai.file-storage.security.signing-key=test-signing-key"
        };
    }

    /**
     * 返回 minio provider 所需的完整属性。
     *
     * @return MinIO provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String[] minioProperties() {
        return new String[]{
                "dinghai.file-storage.provider=minio",
                "dinghai.file-storage.minio.endpoint=http://localhost:9000",
                "dinghai.file-storage.minio.bucket=test-bucket",
                "dinghai.file-storage.minio.access-key=test-access-key",
                "dinghai.file-storage.minio.secret-key=test-secret-key"
        };
    }

    /**
     * 返回本次测试可用的本地根目录。
     *
     * @return 临时本地根目录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String storageRoot() {
        return temporaryFolder.getRoot().toPath().toString();
    }
}
