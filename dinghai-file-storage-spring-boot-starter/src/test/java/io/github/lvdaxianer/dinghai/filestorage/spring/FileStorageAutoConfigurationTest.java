package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    /** MinIO provider 也应注册后端中立的受控读取端点和专用执行器。 */
    @Test
    public void shouldRegisterWebReadEndpointForMinioProvider() {
        contextRunner.withPropertyValues(minioProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageWebController.class);
                    assertThat(context).hasSingleBean(FileStorageWebMvcConfiguration.class);
                    assertThat(context).hasBean("fileStorageWebTaskExecutor");
                });
    }

    /** 本地 provider 默认关闭二级磁盘缓存。 */
    @Test
    public void shouldDisableDiskCacheByDefaultForLocalProvider() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> assertThat(context).doesNotHaveBean(ProviderFileReadCache.class));
    }

    /** 非本地 provider 默认开启二级磁盘缓存。 */
    @Test
    public void shouldEnableDiskCacheByDefaultForNonLocalProvider() {
        contextRunner.withPropertyValues(minioProperties())
                .run(context -> assertThat(context).hasSingleBean(ProviderFileReadCache.class));
    }

    /** 本地 provider 显式开启缓存后应创建二级缓存。 */
    @Test
    public void shouldEnableDiskCacheForLocalProviderWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.cache.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ProviderFileReadCache.class));
    }

    /** 非本地 provider 显式关闭缓存后不得创建二级缓存。 */
    @Test
    public void shouldDisableDiskCacheForNonLocalProviderWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues(minioProperties())
                .withPropertyValues("dinghai.file-storage.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ProviderFileReadCache.class));
    }

    /** 显式启用迁移后自动装配应提供迁移服务所需的 Jackson 编解码器。 */
    @Test
    public void shouldProvideObjectMapperForMigrationService() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.migration.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(FileStorageMigrationService.class);
                });
    }

    /** 未显式启用时不得暴露数据迁移服务或管理端点。 */
    @Test
    public void shouldDisableMigrationByDefault() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageMigrationService.class);
                    assertThat(context).doesNotHaveBean(FileStorageMigrationController.class);
                });
    }

    /** 启用迁移但未提供宿主授权策略时不得注册管理接口。 */
    @Test
    public void shouldNotExposeMigrationControllerWithoutAccessPolicy() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.migration.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FileStorageMigrationController.class));
    }

    /** 启用迁移且宿主提供授权策略时应注册管理接口。 */
    @Test
    public void shouldExposeMigrationControllerWithAccessPolicy() {
        contextRunner.withBean(FileStorageMigrationAccessPolicy.class,
                        () -> (principal, operation) -> true)
                .withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.migration.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(FileStorageMigrationController.class));
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

    /** 启用稳定读取时缺少逻辑存储别名必须在启动时失败。 */
    @Test
    public void shouldFailFastWhenStableAccessHasNoStorageAlias() {
        contextRunner.withPropertyValues(
                        "dinghai.file-storage.provider=local",
                        "dinghai.file-storage.local.root=" + storageRoot(),
                        "dinghai.file-storage.security.signing-key=test-signing-key",
                        "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b")
                .run(context -> assertThat(context).hasFailed());
    }

    /** 启用稳定读取时缺少活动 ID 密钥必须在启动时失败。 */
    @Test
    public void shouldFailFastWhenStableAccessHasNoIdKey() {
        contextRunner.withPropertyValues(
                        "dinghai.file-storage.provider=local",
                        "dinghai.file-storage.local.root=" + storageRoot(),
                        "dinghai.file-storage.security.signing-key=test-signing-key",
                        "dinghai.file-storage.storage-alias=documents")
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
                    assertThat(properties.getUpload().getMaxSize())
                            .isEqualTo(UploadRequest.DEFAULT_MAX_SIZE_BYTES);
                });
    }

    /**
     * Web 流式响应应使用容量明确的专用执行器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldConfigureBoundedFileStorageWebExecutor() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean("fileStorageWebTaskExecutor",
                            ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isPositive();
                    assertThat(executor.getMaxPoolSize()).isGreaterThanOrEqualTo(executor.getCorePoolSize());
                    assertThat(executor.getQueueCapacity()).isPositive();
                    assertThat(executor.getThreadNamePrefix()).contains("file-storage-web-");
                    assertThat(context).hasSingleBean(FileStorageWebMvcConfiguration.class);
                });
    }

    /**
     * 禁用 Web 集成时不应创建文件存储执行器。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldNotConfigureWebExecutorWhenWebIsDisabled() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("fileStorageWebTaskExecutor"));
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
                "dinghai.file-storage.security.signing-key=test-signing-key",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b",
                "dinghai.file-storage.migration.export-root=" + migrationRoot("export"),
                "dinghai.file-storage.migration.import-root=" + migrationRoot("import"),
                "dinghai.file-storage.migration.completed-root=" + migrationRoot("completed"),
                "dinghai.file-storage.migration.failed-root=" + migrationRoot("failed")
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
                "dinghai.file-storage.minio.secret-key=test-secret-key",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b",
                "dinghai.file-storage.migration.export-root=" + migrationRoot("export"),
                "dinghai.file-storage.migration.import-root=" + migrationRoot("import"),
                "dinghai.file-storage.migration.completed-root=" + migrationRoot("completed"),
                "dinghai.file-storage.migration.failed-root=" + migrationRoot("failed")
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

    /** 返回本次测试可用的迁移根目录。 */
    private String migrationRoot(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name).toString();
    }
}
