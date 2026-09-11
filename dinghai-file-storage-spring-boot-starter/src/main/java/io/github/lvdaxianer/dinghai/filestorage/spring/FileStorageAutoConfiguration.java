package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioStorageConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
     * 创建唯一的本地或 MinIO 文件存储 Bean。
     *
     * @param properties 已绑定的文件存储属性
     * @return 已配置的文件存储实现
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage fileStorage(FileStorageProperties properties) {
        validateWebPathPrefix(properties.getWeb().getPathPrefix());
        if (properties.getProvider() == FileStorageProvider.LOCAL) {
            return localStorage(properties);
        }
        if (properties.getProvider() == FileStorageProvider.MINIO) {
            return minioStorage(properties);
        }
        throw invalidConfiguration("file storage provider is required or invalid", null);
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
    @ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "minio")
    public ProviderFileReadCache providerFileReadCache(FileStorage storage,
                                                       FileStorageProperties properties) {
        FileStorageProperties.Cache cache = properties.getCache();
        if (cache.getRoot() == null) {
            throw invalidConfiguration("non-local cache root is required", null);
        }
        return new ProviderFileReadCache(storage, properties.getStorageAlias(), cache.getRoot(),
                cache.isEnabled(), cache.getMaxObjectSize());
    }

    /** 创建非本地 provider 缓存的午夜清理任务。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "minio")
    public ProviderFileReadCacheCleaner providerFileReadCacheCleaner(ProviderFileReadCache cache,
                                                                      FileStorageProperties properties) {
        return new ProviderFileReadCacheCleaner(cache, Clock.systemDefaultZone(),
                properties.getCache().getIdleTtl());
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
                                                              Optional<ProviderFileReadCache> readCache) {
        return new FileStorageWebController(fileStorage, authorizer, Clock.systemUTC(), stableAccess,
                readCache.orElse(null));
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
    private FileStorage localStorage(FileStorageProperties properties) {
        if (properties.getLocal().getRoot() == null) {
            throw invalidConfiguration("local storage root is required", null);
        }
        String signingKey = properties.getSecurity().getSigningKey();
        if (signingKey == null || signingKey.isBlank()) {
            throw invalidConfiguration("local signing key is required", null);
        }
        return new LocalFileStorageAdapter(properties.getLocal().getRoot(),
                signingKey.getBytes(StandardCharsets.UTF_8), properties.getUpload().getMaxSize(),
                properties.getUpload().getContentTypeWhitelist(),
                properties.getSecurity().getMaximumPresignExpiry(), Clock.systemUTC(),
                properties.getWeb().getPathPrefix());
    }

    /**
     * 创建固定 bucket 的 MinIO 适配器。
     *
     * @param properties 已绑定的文件存储属性
     * @return MinIO 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorage minioStorage(FileStorageProperties properties) {
        FileStorageProperties.Minio minio = properties.getMinio();
        try {
            return new MinioFileStorageAdapter(new MinioStorageConfiguration(minio.getEndpoint(),
                    minio.getBucket(), minio.getAccessKey(), minio.getSecretKey(),
                    minio.getConnectTimeout(), minio.getReadTimeout(), minio.getWriteTimeout()));
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration("minio configuration is incomplete", exception);
        }
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
}
