package io.github.lvdaxianer.dinghai.filestorage.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aliyun.sdk.service.oss2.OSSClient;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssClientFactory;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.aliyunoss.AliyunOssStorageConfiguration;
import io.github.lvdaxianer.dinghai.filestorage.rustfs.RustfsFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.rustfs.RustfsStorageConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 按配置选择唯一文件存储后端的 Spring Boot 自动装配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties.class)
@EnableScheduling
public class FileStorageAutoConfiguration {

    /** 文件存储 Web 异步核心线程数。 */
    private static final int WEB_EXECUTOR_CORE_SIZE = 4;
    /** 文件存储 Web 异步最大线程数。 */
    private static final int WEB_EXECUTOR_MAX_SIZE = 16;
    /** 文件存储 Web 异步队列容量。 */
    private static final int WEB_EXECUTOR_QUEUE_CAPACITY = 64;
    /** 文件存储 Web 异步关闭等待秒数。 */
    private static final int WEB_EXECUTOR_SHUTDOWN_SECONDS = 30;

    /**
     * 创建唯一的本地、MinIO、RustFS 或 OSS 文件存储 Bean。
     *
     * @param properties 已绑定的文件存储属性
     * @return 已配置的文件存储实现
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage fileStorage(FileStorageProperties properties, Environment environment,
                                   Optional<OSSClient> aliyunOssClient) {
        validateWebPathPrefix(properties.getWeb().getPathPrefix());
        validateMultipartProperties(properties.getMultipart());
        String bucket = validateBucket(properties.getBucket());
        return selectedFileStorage(properties, environment, bucket, aliyunOssClient);
    }

    /**
     * 根据已选 provider 创建唯一的文件存储适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @param environment Spring 运行环境
     * @param bucket 已校验公共 bucket
     * @param ossClient 可选的受管 OSS SDK client
     * @return 唯一选中的文件存储实现
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private FileStorage selectedFileStorage(FileStorageProperties properties, Environment environment,
                                            String bucket, Optional<OSSClient> aliyunOssClient) {
        // Local 需要额外校验缓存与主数据根目录不会重叠。
        if (properties.getProvider() == FileStorageProvider.LOCAL) {
            validateLocalCacheConfiguration(properties, environment, bucket);
            return localStorage(properties, bucket);
        }
        return remoteFileStorage(properties, bucket, aliyunOssClient);
    }

    /**
     * 根据远端 provider 创建唯一的对象存储适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @param bucket 已校验公共 bucket
     * @param ossClient 可选的受管 OSS SDK client
     * @return 唯一选中的远端文件存储实现
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private FileStorage remoteFileStorage(FileStorageProperties properties, String bucket,
                                          Optional<OSSClient> aliyunOssClient) {
        // MinIO 使用其 S3 兼容客户端。
        if (properties.getProvider() == FileStorageProvider.MINIO) {
            return minioStorage(properties, bucket);
        }
        // RustFS 复用独立的 S3 兼容适配器。
        if (properties.getProvider() == FileStorageProvider.RUSTFS) {
            return rustfsStorage(properties, bucket);
        }
        // OSS 只能使用由 Spring 管理生命周期的原生 SDK client。
        if (properties.getProvider() == FileStorageProvider.ALIYUN_OSS) {
            return aliyunOssStorage(properties, bucket, aliyunOssClient.orElseThrow(
                    () -> invalidConfiguration("aliyun oss client is required", null)));
        }
        throw invalidConfiguration("file storage provider is required or invalid", null);
    }

    /**
     * 创建仅在 OSS provider 被选中时存在的受管 OSS SDK client。
     *
     * @param properties 已绑定的文件存储属性
     * @return 由 Spring 在容器关闭时 shutdown 的 OSS SDK client
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OSSClient.class)
    @ConditionalOnExpression("'${dinghai.file-storage.provider:}' == 'aliyun-oss'")
    public OSSClient aliyunOssClient(FileStorageProperties properties) {
        try {
            return AliyunOssClientFactory.create(aliyunOssConfiguration(properties));
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("aliyun oss configuration is incomplete", exception);
        }
    }

    /**
     * 创建默认拒绝的直接访问授权器。
     *
     * @param properties 已绑定的文件存储属性
     * @param policy 宿主可选授权策略
     * @return 直接访问授权器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageDirectAccessAuthorizer fileStorageDirectAccessAuthorizer(
            FileStorageProperties properties, Optional<FileStorageAccessPolicy> policy) {
        return new FileStorageDirectAccessAuthorizer(properties.getWeb().isDirectAccess(),
                policy.orElse(null));
    }

    /**
     * 创建稳定文件 ID 的生成与解析服务。
     *
     * @param properties 已绑定的文件存储属性
     * @return 稳定文件读取服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnMissingBean
    public StableFileAccessService stableFileAccessService(FileStorageProperties properties) {
        String storageAlias = requireText(properties.getStorageAlias(), "storage alias");
        String key = requireText(properties.getSecurity().getStableIdKey(), "stable ID key");
        try {
            return new StableFileAccessService(storageAlias, key.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("stable file access configuration is invalid", exception);
        }
    }

    /**
     * 为非本地 provider 创建默认启用的二级磁盘读取缓存。
     *
     * @param storage 已装配的远程文件存储
     * @param properties 已绑定的文件存储属性
     * @return 读取缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${dinghai.file-storage.provider:}' == 'minio' "
            + "or '${dinghai.file-storage.provider:}' == 'rustfs' "
            + "or '${dinghai.file-storage.provider:}' == 'aliyun-oss'")
    @ConditionalOnProperty(name = "dinghai.file-storage.cache.enabled", havingValue = "true",
            matchIfMissing = true)
    public ProviderFileReadCache providerFileReadCache(FileStorage storage,
                                                       FileStorageProperties properties) {
        FileStorageProperties.Cache cache = properties.getCache();
        if (cache.getRoot() == null) {
            throw invalidConfiguration("non-local cache root is required", null);
        }
        validateResourceProperties(properties);
        return new ProviderFileReadCache(storage, properties.getStorageAlias(), cache.getRoot(),
                cache.isEnabled(), cache.getMaxObjectSize(), intSize(cache.getBufferSize().toBytes()),
                cache.getMaxConcurrentFills(), cache.getQueueCapacity());
    }

    /** 创建非本地 provider 缓存的午夜清理任务。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${dinghai.file-storage.provider:}' == 'minio' "
            + "or '${dinghai.file-storage.provider:}' == 'rustfs' "
            + "or '${dinghai.file-storage.provider:}' == 'aliyun-oss'")
    @ConditionalOnProperty(name = "dinghai.file-storage.cache.enabled", havingValue = "true",
            matchIfMissing = true)
    public ProviderFileReadCacheCleaner providerFileReadCacheCleaner(ProviderFileReadCache cache,
                                                                      FileStorageProperties properties) {
        return new ProviderFileReadCacheCleaner(cache, Clock.systemDefaultZone(),
                properties.getCache().getIdleTtl());
    }

    /**
     * 创建服务器目录迁移服务。
     *
     * @param storage 当前文件存储 provider
     * @param properties 已绑定的文件存储属性
     * @param objectMapper 迁移清单 JSON 编解码器
     * @return 迁移服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "dinghai.file-storage.migration.enabled", havingValue = "true")
    public FileStorageMigrationService fileStorageMigrationService(FileStorage storage,
                                                                    FileStorageProperties properties,
                                                                    ObjectMapper objectMapper) {
        FileStorageProperties.Migration migration = properties.getMigration();
        requirePath(migration.getExportRoot(), "migration export root");
        requirePath(migration.getImportRoot(), "migration import root");
        requirePath(migration.getCompletedRoot(), "migration completed root");
        requirePath(migration.getFailedRoot(), "migration failed root");
        return new FileStorageMigrationService(storage, properties.getStorageAlias(),
                migration.getExportRoot(), objectMapper);
    }

    /**
     * 提供迁移清单所需的默认 JSON 编解码器，允许宿主应用替换。
     *
     * @return 默认 JSON 编解码器
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    @ConditionalOnProperty(name = "dinghai.file-storage.migration.enabled", havingValue = "true")
    public ObjectMapper fileStorageMigrationObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * 创建受显式启用和宿主授权保护的服务器目录迁移接口。
     *
     * @param migrationService 迁移服务
     * @param properties 已绑定的文件存储属性
     * @param accessPolicy 宿主迁移授权策略
     * @return 迁移管理接口
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FileStorageMigrationAccessPolicy.class)
    @ConditionalOnProperty(name = "dinghai.file-storage.migration.enabled", havingValue = "true")
    public FileStorageMigrationController fileStorageMigrationController(
            FileStorageMigrationService migrationService, FileStorageProperties properties,
            FileStorageMigrationAccessPolicy accessPolicy) {
        return new FileStorageMigrationController(migrationService, properties, accessPolicy);
    }

    /**
     * 为已配置 provider 注册受签名保护的 Web 文件端点。
     *
     * @param fileStorage 已装配文件存储
     * @param authorizer 直接访问授权器
     * @return 文件端点控制器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnProperty(name = "dinghai.file-storage.web.enabled", havingValue = "true",
            matchIfMissing = true)
    public FileStorageWebController fileStorageWebController(FileStorage fileStorage,
                                                              FileStorageDirectAccessAuthorizer authorizer,
                                                              StableFileAccessService stableAccess,
                                                              Optional<ProviderFileReadCache> readCache,
                                                              Optional<WebTransferRateLimitKeyResolver> keyResolver,
                                                              FileStorageProperties properties) {
        validateResourceProperties(properties);
        return new FileStorageWebController(fileStorage, authorizer, Clock.systemUTC(), stableAccess,
                readCache.orElse(null), new WebTransferConfiguration(
                intSize(properties.getWeb().getReadBufferSize().toBytes()),
                properties.getWeb().getMaxConcurrentReads(), properties.getWeb().getMaxConcurrentUploads(),
                properties.getWeb().getMaxConcurrentTransfersPerKey(), keyResolver.orElse(null),
                properties.getWeb().getPathPrefix(),
                properties.getWeb().isSendfileBinaryContentTypesEnabled()));
    }

    /**
     * 创建容量有界的文件存储 Web 异步执行器。
     *
     * @return 文件存储 Web 异步执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean(name = "fileStorageWebTaskExecutor")
    @ConditionalOnMissingBean(name = "fileStorageWebTaskExecutor")
    @ConditionalOnProperty(name = "dinghai.file-storage.web.enabled", havingValue = "true",
            matchIfMissing = true)
    public ThreadPoolTaskExecutor fileStorageWebTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WEB_EXECUTOR_CORE_SIZE);
        executor.setMaxPoolSize(WEB_EXECUTOR_MAX_SIZE);
        executor.setQueueCapacity(WEB_EXECUTOR_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("file-storage-web-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(WEB_EXECUTOR_SHUTDOWN_SECONDS);
        executor.initialize();
        return executor;
    }

    /**
     * 将文件存储 Web 请求接入有界异步执行器。
     *
     * @param executor 文件存储 Web 异步执行器
     * @return Web MVC 异步配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnMissingBean(FileStorageWebMvcConfiguration.class)
    @ConditionalOnProperty(name = "dinghai.file-storage.web.enabled", havingValue = "true",
            matchIfMissing = true)
    public FileStorageWebMvcConfiguration fileStorageWebMvcConfiguration(
            @Qualifier("fileStorageWebTaskExecutor") ThreadPoolTaskExecutor executor) {
        return new FileStorageWebMvcConfiguration(executor);
    }

    /**
     * 注册文件端点的稳定异常映射。
     *
     * @return 文件存储异常处理器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageExceptionHandler fileStorageExceptionHandler() {
        return new FileStorageExceptionHandler();
    }

    /**
     * 创建本地磁盘适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @return 本地适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorage localStorage(FileStorageProperties properties, String bucket) {
        if (properties.getLocal().getRoot() == null) {
            throw invalidConfiguration("local storage root is required", null);
        }
        String signingKey = properties.getSecurity().getSigningKey();
        if (signingKey == null || signingKey.isBlank()) {
            throw invalidConfiguration("local signing key is required", null);
        }
        return new LocalFileStorageAdapter(resolveLocalBucketRoot(properties.getLocal().getRoot(), bucket),
                signingKey.getBytes(StandardCharsets.UTF_8), properties.getUpload().getMaxSize(),
                properties.getUpload().getContentTypeWhitelist(),
                properties.getSecurity().getMaximumPresignExpiry(), Clock.systemUTC(),
                properties.getWeb().getPathPrefix(), properties.getMultipart().getLocalRoot());
    }

    /**
     * 创建固定 bucket 的 MinIO 适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @return MinIO 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorage minioStorage(FileStorageProperties properties, String bucket) {
        FileStorageProperties.Minio minio = properties.getMinio();
        try {
            return new MinioFileStorageAdapter(new MinioStorageConfiguration(minio.getEndpoint(),
                    bucket, minio.getAccessKey(), minio.getSecretKey(),
                    minio.getConnectTimeout(), minio.getReadTimeout(), minio.getWriteTimeout()));
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("minio configuration is incomplete", exception);
        }
    }

    /**
     * 创建固定 bucket 的 RustFS 适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @param bucket 已校验公共 bucket
     * @return RustFS 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private FileStorage rustfsStorage(FileStorageProperties properties, String bucket) {
        FileStorageProperties.Rustfs rustfs = properties.getRustfs();
        try {
            return new RustfsFileStorageAdapter(new RustfsStorageConfiguration(rustfs.getEndpoint(),
                    bucket, rustfs.getAccessKey(), rustfs.getSecretKey(),
                    rustfs.getConnectTimeout(), rustfs.getReadTimeout(), rustfs.getWriteTimeout()));
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("rustfs configuration is incomplete", exception);
        }
    }

    /**
     * 创建使用受管 SDK client 的固定 bucket OSS 适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @param bucket 已校验公共 bucket
     * @param client 受 Spring 管理的 OSS SDK client
     * @return OSS 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private FileStorage aliyunOssStorage(FileStorageProperties properties, String bucket,
                                         OSSClient client) {
        try {
            AliyunOssStorageConfiguration configuration = aliyunOssConfiguration(properties);
            return new AliyunOssFileStorageAdapter(client, bucket);
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("aliyun oss configuration is incomplete", exception);
        }
    }

    /**
     * 将 Starter 属性转换为 OSS SDK 所需的已校验连接配置。
     *
     * @param properties 已绑定的文件存储属性
     * @return 已校验 OSS 连接配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private AliyunOssStorageConfiguration aliyunOssConfiguration(FileStorageProperties properties) {
        FileStorageProperties.AliyunOss aliyunOss = properties.getAliyunOss();
        return new AliyunOssStorageConfiguration(aliyunOss.getRegion(), properties.getBucket(),
                aliyunOss.getConnectTimeout(), aliyunOss.getReadTimeout(),
                aliyunOss.getWriteTimeout());
    }

    /**
     * 校验 Web 路径前缀不会被解释为查询串或片段。
     *
     * @param pathPrefix 文件端点路径前缀
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void validateWebPathPrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank() || !pathPrefix.startsWith("/")) {
            throw invalidConfiguration("file storage web path prefix is invalid", null);
        }
        if (pathPrefix.indexOf('?') >= 0 || pathPrefix.indexOf('#') >= 0
                || pathPrefix.indexOf('\r') >= 0 || pathPrefix.indexOf('\n') >= 0) {
            throw invalidConfiguration("file storage web path prefix is invalid", null);
        }
    }

    /**
     * 校验公共 bucket 可安全地映射为本地单层目录。
     *
     * @param bucket 待校验的公共 bucket
     * @return 已校验的公共 bucket
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private String validateBucket(String bucket) {
        String value = requireText(bucket, "file storage bucket");
        if (value.equals(".") || value.equals("..") || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidConfiguration("file storage bucket is invalid", null);
        }
        return value;
    }

    /**
     * 将公共 bucket 解析为位于本地对象根目录下的专属目录。
     *
     * @param localRoot 本地对象根目录
     * @param bucket 已校验的公共 bucket
     * @return 归属于 bucket 的本地对象根目录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private Path resolveLocalBucketRoot(Path localRoot, String bucket) {
        Path normalizedRoot = localRoot.toAbsolutePath().normalize();
        Path bucketRoot = normalizedRoot.resolve(bucket).normalize();
        if (!bucketRoot.startsWith(normalizedRoot)) {
            throw invalidConfiguration("file storage bucket is invalid", null);
        }
        return bucketRoot;
    }

    /**
     * 校验 Local 主存储不会复用可清理缓存或分片临时目录，并拒绝已移除的显式缓存配置。
     *
     * @param properties 已绑定的文件存储属性
     * @param environment Spring 运行环境
     * @param bucket 已校验的公共 bucket
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void validateLocalCacheConfiguration(FileStorageProperties properties,
                                                 Environment environment, String bucket) {
        if ("true".equalsIgnoreCase(environment.getProperty("dinghai.file-storage.cache.enabled"))) {
            throw invalidConfiguration("local disk cache has been removed; remove cache.enabled", null);
        }
        Path cacheRoot = properties.getCache().getRoot();
        Path localRoot = properties.getLocal().getRoot();
        if (localRoot == null) {
            return;
        }
        Path primaryRoot = resolveLocalBucketRoot(localRoot, bucket);
        if (cacheRoot != null) {
            Path normalizedCache = cacheRoot.toAbsolutePath().normalize();
            // 缓存清理不能触及正式对象目录。
            if (normalizedCache.startsWith(primaryRoot) || primaryRoot.startsWith(normalizedCache)) {
                throw invalidConfiguration("cache root overlaps local storage root", null);
            }
        }
        Path multipartRoot = properties.getMultipart().getLocalRoot().toAbsolutePath().normalize();
        // multipart 会话清理不能触及正式对象目录。
        if (multipartRoot.startsWith(primaryRoot) || primaryRoot.startsWith(multipartRoot)) {
            throw invalidConfiguration("multipart root overlaps local storage root", null);
        }
    }

    private void validateResourceProperties(FileStorageProperties properties) {
        FileStorageProperties.Web web = properties.getWeb();
        FileStorageProperties.Cache cache = properties.getCache();
        if (web.getReadBufferSize() == null || web.getReadBufferSize().toBytes() <= 0L
                || web.getMaxConcurrentReads() <= 0
                || web.getMaxConcurrentUploads() <= 0
                || cache.getBufferSize() == null || cache.getBufferSize().toBytes() <= 0L
                || cache.getMaxConcurrentFills() <= 0 || cache.getQueueCapacity() <= 0) {
            throw invalidConfiguration("file storage resource limits must be positive", null);
        }
        if (web.getMaxConcurrentTransfersPerKey() < 0) {
            throw invalidConfiguration("file storage per-key transfer limit must not be negative", null);
        }
    }

    /**
     * 将 multipart 属性错误映射为不会泄露部署细节的稳定启动失败。
     *
     * @param multipart multipart 上传配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private void validateMultipartProperties(FileStorageProperties.Multipart multipart) {
        try {
            multipart.validate();
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("multipart configuration is invalid", exception);
        }
    }

    private int intSize(long value) {
        if (value > Integer.MAX_VALUE) {
            throw invalidConfiguration("file storage buffer size is too large", null);
        }
        return (int) value;
    }

    /**
     * 创建不泄露敏感配置的稳定异常。
     *
     * @param message 安全消息
     * @param cause 底层原因
     * @return 配置异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException invalidConfiguration(String message, Throwable cause) {
        return new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION, message, cause);
    }

    /** 读取并校验不可为空的安全配置文本。 */
    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalidConfiguration(name + " is required", null);
        }
        return value;
    }

    /** 校验迁移目录配置。 */
    private void requirePath(java.nio.file.Path value, String name) {
        if (value == null) {
            throw invalidConfiguration(name + " is required", null);
        }
    }
}
