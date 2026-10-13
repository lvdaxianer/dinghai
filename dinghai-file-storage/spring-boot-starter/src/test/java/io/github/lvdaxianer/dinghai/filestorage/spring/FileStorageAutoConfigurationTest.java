package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import io.github.lvdaxianer.dinghai.threadpool.spring.ThreadPoolAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration.class,
                    ThreadPoolAutoConfiguration.class));

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
                });
    }

    /**
     * local provider 应将对象保存到公共 bucket 子目录。
     *
     * @throws Exception 写入或读取临时文件失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldStoreLocalObjectUnderConfiguredBucket() throws Exception {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    FileStorage storage = context.getBean(FileStorage.class);
                    ObjectKey key = ObjectKey.of("1/1/1/1.png");
                    byte[] content = "file-content".getBytes(StandardCharsets.UTF_8);

                    storage.put(UploadRequest.of(key, new ByteArrayInputStream(content),
                            content.length, "image/png"));

                    Path expected = Path.of(storageRoot()).resolve("documents").resolve(key.value());
                    assertThat(Files.readString(expected)).isEqualTo("file-content");
                });
    }

    /**
     * 本地 provider 默认应在删除对象后清理空目录。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    @Test
    public void shouldCleanEmptyDirectoriesByDefaultForLocalProvider() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    FileStorage storage = context.getBean(FileStorage.class);
                    ObjectKey key = ObjectKey.of("model-version/1/V0/model.bin");
                    storage.put(UploadRequest.of(key, new ByteArrayInputStream(new byte[]{1}), 1L,
                            "application/octet-stream"));

                    assertThat(storage.delete(key)).isTrue();
                    assertThat(Files.exists(Path.of(storageRoot()).resolve(
                            "documents/model-version/1/V0")))
                            .isFalse();
                });
    }

    /**
     * 本地 provider 关闭清理开关后应保留空目录。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    @Test
    public void shouldKeepEmptyDirectoriesWhenCleanupIsDisabled() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.local.cleanup-empty-directories=false")
                .run(context -> {
                    FileStorage storage = context.getBean(FileStorage.class);
                    ObjectKey key = ObjectKey.of("model-version/1/V0/model.bin");
                    storage.put(UploadRequest.of(key, new ByteArrayInputStream(new byte[]{1}), 1L,
                            "application/octet-stream"));

                    assertThat(storage.delete(key)).isTrue();
                    assertThat(Files.isDirectory(Path.of(storageRoot()).resolve(
                            "documents/model-version/1/V0")))
                            .isTrue();
                });
    }

    /**
     * 本地 provider 的预签名地址必须使用与 MVC 相同的配置化操作路径。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldUseConfiguredOperationPathsForLocalPresignedUrls() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.path-prefix=rsm",
                        "dinghai.file-storage.web.upload-path=upload-file",
                        "dinghai.file-storage.web.download-path=download-file",
                        "dinghai.file-storage.web.preview-path=preview-file")
                .run(context -> {
                    FileStorage storage = context.getBean(FileStorage.class);
                    ObjectKey key = ObjectKey.of("documents/read me.txt");

                    assertThat(storage.presignUpload(new PresignUploadRequest(key, Duration.ofMinutes(1L),
                            "text/plain", 1024L)).url()).startsWith("/rsm/upload-file/");
                    assertThat(storage.presignDownload(new PresignDownloadRequest(key, Duration.ofMinutes(1L),
                            PresignDownloadRequest.ResponseDisposition.ATTACHMENT,
                            io.github.lvdaxianer.dinghai.filestorage.core.CacheControl.privateNoCache())).url())
                            .startsWith("/rsm/download-file/");
                    assertThat(storage.presignDownload(new PresignDownloadRequest(key, Duration.ofMinutes(1L),
                            PresignDownloadRequest.ResponseDisposition.INLINE,
                            io.github.lvdaxianer.dinghai.filestorage.core.CacheControl.privateNoCache())).url())
                            .startsWith("/rsm/preview-file/");
                });
    }

    /**
     * 非法 Web 路由值必须在自动装配阶段以统一错误码终止启动。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldRejectUnsafeWebRouteConfigurationAtStartup() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.read-path=/read?unsafe=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = rootCause(context.getStartupFailure());
                    assertThat(failure).isInstanceOf(FileStorageException.class);
                    assertThat(((FileStorageException) failure).errorCode())
                            .isEqualTo(FileStorageErrorCode.INVALID_CONFIGURATION);
                });
    }

    /** 选中的 provider 必须由匹配的 factory 创建，基础 Starter 不直接依赖厂商 SDK。 */
    @Test
    public void shouldCreateSelectedRemoteStorageThroughProviderFactory() {
        FileStorage storage = mock(FileStorage.class);
        FileStorageProviderFactory factory = mock(FileStorageProviderFactory.class);
        when(factory.provider()).thenReturn(FileStorageProvider.MINIO);
        when(factory.create(any(FileStorageProperties.class), eq("test-bucket"))).thenReturn(storage);

        contextRunner.withBean(FileStorageProviderFactory.class, () -> factory)
                .withPropertyValues(minioProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context.getBean(FileStorage.class)).isSameAs(storage);
                });
    }

    /** MinIO provider 也应注册后端中立的受控读取端点和专用执行器。 */
    @Test
    public void shouldRegisterWebReadEndpointForMinioProvider() {
        withProviderFactory(FileStorageProvider.MINIO)
                .withPropertyValues(minioProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageWebController.class);
                    assertThat(context).hasSingleBean(FileStorageWebMvcConfiguration.class);
                    assertThat(context).hasBean("fileStorageWebTaskExecutor");
                });
    }

    /** multipart 控制器仅在宿主提供对象访问策略时才暴露。 */
    @Test
    public void shouldExposeMultipartControllerOnlyWithAccessPolicy() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MultipartUploadService.class);
                    assertThat(context).doesNotHaveBean(MultipartUploadController.class);
                    assertThat(context).doesNotHaveBean(MultipartUploadSessionCleaner.class);
                });

        contextRunner.withBean(FileStorageAccessPolicy.class, () -> (principal, key, operation) -> true)
                .withPropertyValues(localProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(MultipartUploadService.class);
                    assertThat(context).hasSingleBean(MultipartUploadController.class);
                    assertThat(context).hasSingleBean(MultipartUploadSessionCleaner.class);
                });
    }

    /** 本地 provider 默认关闭二级磁盘缓存。 */
    @Test
    public void shouldDisableDiskCacheByDefaultForLocalProvider() {
        contextRunner.withPropertyValues(localProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ProviderFileReadCache.class);
                    assertThat(context).doesNotHaveBean(ProviderFileReadCacheCleaner.class);
                });
    }

    /** 非本地 provider 默认开启二级磁盘缓存。 */
    @Test
    public void shouldEnableDiskCacheByDefaultForNonLocalProvider() {
        withProviderFactory(FileStorageProvider.MINIO)
                .withPropertyValues(minioProperties())
                .run(context -> assertThat(context).hasSingleBean(ProviderFileReadCache.class));
    }

    /** RustFS 作为远端 provider 默认启用二级磁盘缓存及其清理器。 */
    @Test
    public void shouldEnableDiskCacheByDefaultForRustfsProvider() {
        withProviderFactory(FileStorageProvider.RUSTFS)
                .withPropertyValues(rustfsProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(ProviderFileReadCache.class);
                    assertThat(context).hasSingleBean(ProviderFileReadCacheCleaner.class);
                });
    }

    /** 本地 provider 显式开启已移除的二级缓存配置必须启动失败。 */
    @Test
    public void shouldRejectExplicitDiskCacheForLocalProvider() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("local disk cache");
                });
    }

    /** 本地主 bucket 与缓存目录重叠时必须拒绝启动，避免清理器误删主数据。 */
    @Test
    public void shouldRejectOverlappingLocalStorageAndCacheRoots() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.cache.root=" + storageRoot())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("cache root overlaps");
                });
    }

    /** 非本地 provider 显式关闭缓存后不得创建二级缓存。 */
    @Test
    public void shouldDisableDiskCacheForNonLocalProviderWhenExplicitlyDisabled() {
        withProviderFactory(FileStorageProvider.MINIO)
                .withPropertyValues(minioProperties())
                .withPropertyValues("dinghai.file-storage.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ProviderFileReadCache.class));
    }

    /** RustFS 显式关闭缓存后不得创建二级缓存或清理器。 */
    @Test
    public void shouldDisableDiskCacheForRustfsProviderWhenExplicitlyDisabled() {
        withProviderFactory(FileStorageProvider.RUSTFS)
                .withPropertyValues(rustfsProperties())
                .withPropertyValues("dinghai.file-storage.cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ProviderFileReadCache.class);
                    assertThat(context).doesNotHaveBean(ProviderFileReadCacheCleaner.class);
                });
    }


    /** 读取和缓存资源限制必须拒绝非正数配置。 */
    @Test
    public void shouldRejectNonPositiveReadResourceLimits() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.read-buffer-size=0B")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 小于远端共同最小值的分片大小必须在启动期失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldRejectMultipartPartSizeBelowProviderMinimum() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.multipart.part-size=1MB")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining(
                            "multipart configuration is invalid");
                });
    }

    /**
     * multipart 临时目录不得与正式本地存储目录重叠。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldRejectOverlappingLocalStorageAndMultipartRoots() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.multipart.local-root=" + storageRoot())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining(
                            "multipart root overlaps local storage root");
                });
    }

    /**
     * Local provider 必须初始化配置指定的独立分片临时目录。
     *
     * @throws Exception 创建测试目录失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldUseConfiguredMultipartRootForLocalProvider() throws Exception {
        Path multipartRoot = temporaryFolder.getRoot().toPath().resolve("multipart-sessions");

        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.multipart.local-root=" + multipartRoot)
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalFileStorageAdapter.class);
                    assertThat(Files.isDirectory(multipartRoot)).isTrue();
                });
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
        contextRunner.withPropertyValues(
                        "dinghai.file-storage.provider=unknown",
                        "dinghai.file-storage.bucket=documents")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 远端 provider 缺少对应 Starter 时必须以明确配置错误失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    @Test
    public void shouldFailFastWhenSelectedRemoteProviderStarterIsMissing() {
        contextRunner.withPropertyValues(minioProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("dinghai-file-storage-minio-spring-boot-starter");
                });
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
                        "dinghai.file-storage.bucket=documents",
                        "dinghai.file-storage.security.signing-key=test-signing-key")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 被选中的 RustFS provider 缺少 endpoint 时必须失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void shouldFailFastWhenSelectedRustfsProviderIsIncomplete() {
        contextRunner.withPropertyValues(rustfsPropertiesWithoutEndpoint())
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 旧 OSS provider 值必须在属性绑定阶段拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    @Test
    public void shouldRejectLegacyOssProvider() {
        contextRunner.withPropertyValues(legacyOssProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertThat(rootCause(startupFailure).getMessage()).contains("FileStorageProvider.oss");
                    assertThat(startupFailure.getMessage()).doesNotContain("test-secret-key");
                });
    }

    /**
     * 所有 provider 缺少公共 bucket 时必须在启动期失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldFailFastWhenSelectedProviderHasNoPublicBucket() {
        contextRunner.withPropertyValues(localPropertiesWithoutBucket())
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 遗留 MinIO 子配置不能替代必填公共 bucket。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldNotFallbackToLegacyMinioBucket() {
        contextRunner.withPropertyValues(minioPropertiesWithoutPublicBucket())
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 公共 bucket 不能作为本地文件系统路径使用。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldFailFastForUnsafePublicBucket() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.bucket=documents/archive")
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
                        "dinghai.file-storage.bucket=documents",
                        "dinghai.file-storage.local.root=" + storageRoot())
                .run(context -> assertThat(context).hasFailed());
    }

    /** 启用稳定读取时缺少逻辑存储别名必须在启动时失败。 */
    @Test
    public void shouldFailFastWhenStableAccessHasNoStorageAlias() {
        contextRunner.withPropertyValues(
                        "dinghai.file-storage.provider=local",
                        "dinghai.file-storage.bucket=documents",
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
                        "dinghai.file-storage.bucket=documents",
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
                    assertThat(properties.getWeb().getReadBufferSize().toBytes()).isEqualTo(128L * 1024L);
                    assertThat(properties.getWeb().getMaxConcurrentReads()).isEqualTo(128);
                    assertThat(properties.getWeb().getMaxConcurrentUploads()).isEqualTo(16);
                    assertThat(properties.getWeb().getMaxConcurrentTransfersPerKey()).isZero();
                    assertThat(properties.getWeb().isSendfileBinaryContentTypesEnabled()).isTrue();
                    assertThat(properties.getCache().getBufferSize().toBytes()).isEqualTo(128L * 1024L);
                    assertThat(properties.getCache().getMaxConcurrentFills()).isEqualTo(4);
                    assertThat(properties.getCache().getQueueCapacity()).isEqualTo(32);
                    assertThat(properties.getUpload().getMaxSize())
                            .isEqualTo(UploadRequest.DEFAULT_MAX_SIZE_BYTES);
                });
    }

    /** 二进制 sendfile 优化允许由宿主自定义压缩策略关闭。 */
    @Test
    public void shouldBindBinarySendfileOptimizationSwitch() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.sendfile-binary-content-types-enabled=false")
                .run(context -> assertThat(context.getBean(FileStorageProperties.class).getWeb()
                        .isSendfileBinaryContentTypesEnabled()).isFalse());
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
     * 文件存储 Web 执行器启用治理后必须复用全局并发许可。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldGovernFileStorageWebExecutorWhenManagedPoolIsConfigured() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues(threadPoolProperties())
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean("fileStorageWebTaskExecutor",
                            ThreadPoolTaskExecutor.class);
                    ThreadPoolGovernor governor = context.getBean(ThreadPoolGovernor.class);
                    CountDownLatch firstStarted = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);
                    CountDownLatch secondStarted = new CountDownLatch(1);
                    AtomicInteger concurrent = new AtomicInteger();
                    AtomicInteger maximum = new AtomicInteger();

                    executor.execute(() -> awaitAndTrack(firstStarted, release, concurrent, maximum));
                    assertThat(await(firstStarted)).isTrue();
                    executor.execute(() -> {
                        concurrent.incrementAndGet();
                        secondStarted.countDown();
                        concurrent.decrementAndGet();
                    });

                    assertThat(await(secondStarted, 100)).isFalse();
                    release.countDown();
                    assertThat(await(secondStarted)).isTrue();
                    assertThat(maximum.get()).isEqualTo(1);
                    assertThat(governor.snapshots()).anySatisfy(snapshot -> {
                        assertThat(snapshot.name()).isEqualTo("file-storage-web");
                        assertThat(snapshot.completed()).isEqualTo(2);
                    });
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

    @Test
    public void shouldFailFastForInvalidWebUploadConcurrency() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.max-concurrent-uploads=0")
                .run(context -> assertThat(context).hasFailed());
    }

    /** 每 key 并发上限不得为负数，零用于关闭该可选限制。 */
    @Test
    public void shouldFailFastForNegativeWebTransferConcurrencyPerKey() {
        contextRunner.withPropertyValues(localProperties())
                .withPropertyValues("dinghai.file-storage.web.max-concurrent-transfers-per-key=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 为基础 Starter 测试注册不携带厂商 SDK 的远端 factory。
     *
     * @param provider 需要模拟的 provider
     * @return 带有模拟 factory 的上下文运行器
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    private ApplicationContextRunner withProviderFactory(FileStorageProvider provider) {
        FileStorageProviderFactory factory = mock(FileStorageProviderFactory.class);
        when(factory.provider()).thenReturn(provider);
        when(factory.create(any(FileStorageProperties.class), any(String.class)))
                .thenReturn(mock(FileStorage.class));
        return contextRunner.withBean(FileStorageProviderFactory.class, () -> factory);
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
                "dinghai.file-storage.bucket=documents",
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
        return minioProperties("dinghai.file-storage.bucket=test-bucket");
    }

    /**
     * 返回携带指定 bucket 属性的 MinIO provider 属性。
     *
     * @param bucketProperty MinIO 或公共 bucket 属性
     * @return 完整的 MinIO provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private String[] minioProperties(String bucketProperty) {
        return new String[]{
                "dinghai.file-storage.provider=minio",
                bucketProperty,
                "dinghai.file-storage.minio.endpoint=http://localhost:9000",
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
     * 返回仅含遗留 MinIO bucket 的属性，用于验证不兼容旧配置。
     *
     * @return 不含公共 bucket 的 MinIO provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private String[] minioPropertiesWithoutPublicBucket() {
        return minioProperties("dinghai.file-storage.minio.bucket=test-bucket");
    }

    /**
     * 返回 rustfs provider 所需的完整属性。
     *
     * @return RustFS provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private String[] rustfsProperties() {
        return new String[]{
                "dinghai.file-storage.provider=rustfs",
                "dinghai.file-storage.bucket=test-bucket",
                "dinghai.file-storage.rustfs.endpoint=http://localhost:9100",
                "dinghai.file-storage.rustfs.access-key=test-access-key",
                "dinghai.file-storage.rustfs.secret-key=test-secret-key",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b",
                "dinghai.file-storage.migration.export-root=" + migrationRoot("export"),
                "dinghai.file-storage.migration.import-root=" + migrationRoot("import"),
                "dinghai.file-storage.migration.completed-root=" + migrationRoot("completed"),
                "dinghai.file-storage.migration.failed-root=" + migrationRoot("failed")
        };
    }

    /**
     * 返回缺少 RustFS endpoint 的属性。
     *
     * @return 不完整 RustFS provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private String[] rustfsPropertiesWithoutEndpoint() {
        return new String[]{
                "dinghai.file-storage.provider=rustfs",
                "dinghai.file-storage.bucket=test-bucket",
                "dinghai.file-storage.rustfs.endpoint=",
                "dinghai.file-storage.rustfs.access-key=test-access-key",
                "dinghai.file-storage.rustfs.secret-key=test-secret-key",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b",
                "dinghai.file-storage.migration.export-root=" + migrationRoot("export"),
                "dinghai.file-storage.migration.import-root=" + migrationRoot("import"),
                "dinghai.file-storage.migration.completed-root=" + migrationRoot("completed"),
                "dinghai.file-storage.migration.failed-root=" + migrationRoot("failed")
        };
    }

    /**
     * 返回已经废弃的 OSS provider 属性。
     *
     * @return 旧 OSS provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private String[] legacyOssProperties() {
        return new String[]{
                "dinghai.file-storage.provider=oss",
                "dinghai.file-storage.bucket=test-bucket",
                "dinghai.file-storage.storage-alias=documents",
                "dinghai.file-storage.security.stable-id-key=stable-file-id-test-key-material-32b",
                "dinghai.file-storage.migration.export-root=" + migrationRoot("export"),
                "dinghai.file-storage.migration.import-root=" + migrationRoot("import"),
                "dinghai.file-storage.migration.completed-root=" + migrationRoot("completed"),
                "dinghai.file-storage.migration.failed-root=" + migrationRoot("failed")
        };
    }

    /**
     * 获取异常原因链中最深层的异常。
     *
     * @param exception 启动失败异常
     * @return 根因异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 返回缺少公共 bucket 的 local provider 属性。
     *
     * @return 不含公共 bucket 的 local provider 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private String[] localPropertiesWithoutBucket() {
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
     * 返回文件存储 Web 池的最小治理配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private String[] threadPoolProperties() {
        return new String[]{
                "dinghai.thread-pool.enabled=true",
                "dinghai.thread-pool.process-thread-capacity=1",
                "dinghai.thread-pool.managed-worker-budget=1",
                "dinghai.thread-pool.metrics.sqlite-path=" + temporaryFolder.getRoot().toPath()
                        .resolve("thread-pool-metrics.db"),
                "dinghai.thread-pool.pools.file-storage-web.minimum-size=1",
                "dinghai.thread-pool.pools.file-storage-web.maximum-size=1",
                "dinghai.thread-pool.pools.file-storage-web.weight=1",
                "dinghai.thread-pool.pools.file-storage-web.priority=HIGH",
                "dinghai.thread-pool.pools.file-storage-web.reliability=FAIL_FAST",
                "dinghai.thread-pool.pools.file-storage-web.queue-capacity=10"
        };
    }

    /**
     * 在第一个任务占用许可期间维护并发数。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void awaitAndTrack(CountDownLatch started, CountDownLatch release, AtomicInteger concurrent,
                               AtomicInteger maximum) {
        int current = concurrent.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
        started.countDown();
        await(release);
        concurrent.decrementAndGet();
    }

    /**
     * 等待默认超时内的并发协作信号。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private boolean await(CountDownLatch latch) {
        return await(latch, 2_000);
    }

    /**
     * 等待指定时间内的并发协作信号。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test thread interrupted", exception);
        }
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
