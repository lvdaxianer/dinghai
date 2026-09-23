package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs.HuaweiObsCredentialMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 文件存储 Starter 的外部配置属性。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@ConfigurationProperties("dinghai.file-storage")
public class FileStorageProperties {

    /** 存储后端类型。 */
    private FileStorageProvider provider;
    /** 所有存储后端共用的逻辑桶名称。 */
    private String bucket;
    /** 不随物理后端变动的逻辑存储别名。 */
    private String storageAlias;
    /** 本地磁盘后端配置。 */
    private final Local local = new Local();
    /** MinIO 后端配置。 */
    private final Minio minio = new Minio();
    /** RustFS 后端配置。 */
    private final Rustfs rustfs = new Rustfs();
    /** 阿里云 OSS 后端配置。 */
    private final AliyunOss aliyunOss = new AliyunOss();
    /** 华为云 OBS 后端配置。 */
    private final HuaweiCloudObs huaweicloudObs = new HuaweiCloudObs();
    /** 上传策略配置。 */
    private final Upload upload = new Upload();
    /** 大文件分片上传配置。 */
    private final Multipart multipart = new Multipart();
    /** 本地签名配置。 */
    private final Security security = new Security();
    /** Web 访问配置。 */
    private final Web web = new Web();
    /** 非本地 provider 读取缓存配置。 */
    private final Cache cache = new Cache();
    /** 服务器目录迁移配置。 */
    private final Migration migration = new Migration();

    /**
     * 获取后端类型。
     *
     * @return 后端类型
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public FileStorageProvider getProvider() {
        return provider;
    }

    /**
     * 设置后端类型。
     *
     * @param provider 后端类型
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public void setProvider(FileStorageProvider provider) {
        this.provider = provider;
    }

    /**
     * 获取公共逻辑桶名称。
     *
     * @return 公共逻辑桶名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 设置公共逻辑桶名称。
     *
     * @param bucket 公共逻辑桶名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /**
     * 获取逻辑存储别名。
     *
     * @return 逻辑存储别名
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public String getStorageAlias() {
        return storageAlias;
    }

    /**
     * 设置逻辑存储别名。
     *
     * @param storageAlias 逻辑存储别名
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public void setStorageAlias(String storageAlias) {
        this.storageAlias = storageAlias;
    }

    /**
     * 获取本地后端配置。
     *
     * @return 本地后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Local getLocal() {
        return local;
    }

    /**
     * 获取 MinIO 后端配置。
     *
     * @return MinIO 后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Minio getMinio() {
        return minio;
    }

    /**
     * 获取 RustFS 后端配置。
     *
     * @return RustFS 后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public Rustfs getRustfs() {
        return rustfs;
    }

    /**
     * 获取阿里云 OSS 后端配置。
     *
     * @return 阿里云 OSS 后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    public AliyunOss getAliyunOss() {
        return aliyunOss;
    }

    /** 获取华为云 OBS 后端配置。 */
    public HuaweiCloudObs getHuaweicloudObs() {
        return huaweicloudObs;
    }

    /**
     * 获取上传策略配置。
     *
     * @return 上传策略配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Upload getUpload() {
        return upload;
    }

    /**
     * 获取大文件分片上传配置。
     *
     * @return 分片上传资源与会话限制
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public Multipart getMultipart() {
        return multipart;
    }

    /**
     * 获取签名配置。
     *
     * @return 签名配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * 获取 Web 访问配置。
     *
     * @return Web 访问配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Web getWeb() {
        return web;
    }

    /**
     * 获取非本地 provider 读取缓存配置。
     *
     * @return 读取缓存配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * 获取服务器目录迁移配置。
     *
     * @return 服务器目录迁移配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public Migration getMigration() {
        return migration;
    }

    /**
     * 本地磁盘后端属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Local {
        /** 对象根目录。 */
        private Path root;

        /**
         * 获取对象根目录。
         *
         * @return 对象根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Path getRoot() {
            return root;
        }

        /**
         * 设置对象根目录。
         *
         * @param root 对象根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setRoot(Path root) {
            this.root = root;
        }
    }

    /**
     * MinIO 后端属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Minio {
        /** MinIO 服务端地址。 */
        private String endpoint;
        /** 访问密钥。 */
        private String accessKey;
        /** 私密访问密钥。 */
        private String secretKey;
        /** 连接超时。 */
        private Duration connectTimeout = Duration.ofSeconds(10L);
        /** 读取超时。 */
        private Duration readTimeout = Duration.ofSeconds(10L);
        /** 写入超时。 */
        private Duration writeTimeout = Duration.ofSeconds(10L);

        /**
         * 获取 MinIO 服务端地址。
         *
         * @return 服务端地址
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * 设置 MinIO 服务端地址。
         *
         * @param endpoint 服务端地址
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        /**
         * 获取访问密钥。
         *
         * @return 访问密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getAccessKey() {
            return accessKey;
        }

        /**
         * 设置访问密钥。
         *
         * @param accessKey 访问密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        /**
         * 获取私密访问密钥。
         *
         * @return 私密访问密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getSecretKey() {
            return secretKey;
        }

        /**
         * 设置私密访问密钥。
         *
         * @param secretKey 私密访问密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * 获取连接超时。
         *
         * @return 连接超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置连接超时。
         *
         * @param connectTimeout 连接超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 获取读取超时。
         *
         * @return 读取超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Duration getReadTimeout() {
            return readTimeout;
        }

        /**
         * 设置读取超时。
         *
         * @param readTimeout 读取超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        /**
         * 获取写入超时。
         *
         * @return 写入超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Duration getWriteTimeout() {
            return writeTimeout;
        }

        /**
         * 设置写入超时。
         *
         * @param writeTimeout 写入超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setWriteTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
    }

    /**
     * RustFS 后端属性，字段与 S3 兼容的 MinIO 客户端连接项保持一致。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public static class Rustfs extends Minio {
    }

    /**
     * 阿里云 OSS 后端属性，凭证仅由部署环境变量提供。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    public static class AliyunOss {
        /** OSS 地域 ID。 */
        private String region;
        /** 连接超时。 */
        private Duration connectTimeout = Duration.ofSeconds(10L);
        /** 读取超时。 */
        private Duration readTimeout = Duration.ofSeconds(10L);
        /** 写入超时。 */
        private Duration writeTimeout = Duration.ofSeconds(10L);

        /**
         * 获取 OSS 地域 ID。
         *
         * @return OSS 地域 ID
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public String getRegion() {
            return region;
        }

        /**
         * 设置 OSS 地域 ID。
         *
         * @param region OSS 地域 ID
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public void setRegion(String region) {
            this.region = region;
        }

        /**
         * 获取连接超时。
         *
         * @return 连接超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置连接超时。
         *
         * @param connectTimeout 连接超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 获取读取超时。
         *
         * @return 读取超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public Duration getReadTimeout() {
            return readTimeout;
        }

        /**
         * 设置读取超时。
         *
         * @param readTimeout 读取超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        /**
         * 获取写入超时。
         *
         * @return 写入超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public Duration getWriteTimeout() {
            return writeTimeout;
        }

        /**
         * 设置写入超时。
         *
         * @param writeTimeout 写入超时
         * @author lvdaxianer@yeah.net
         * @date 2026-09-14
         */
        public void setWriteTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
    }

    /** 华为云 OBS 后端属性，凭证可使用静态值、STS 或宿主 Provider。 */
    public static class HuaweiCloudObs {
        /** OBS 服务 endpoint。 */
        private String endpoint;
        /** OBS 地域标识。 */
        private String region;
        /** 凭证来源模式。 */
        private HuaweiObsCredentialMode credentialMode = HuaweiObsCredentialMode.STATIC;
        /** 访问密钥。 */
        private String accessKey;
        /** 私密访问密钥。 */
        private String secretKey;
        /** STS 安全令牌。 */
        private String securityToken;
        /** 连接超时。 */
        private Duration connectTimeout = Duration.ofSeconds(10L);
        /** 读取超时。 */
        private Duration readTimeout = Duration.ofSeconds(10L);
        /** 写入超时。 */
        private Duration writeTimeout = Duration.ofSeconds(10L);

        /** 获取 OBS endpoint。 */
        public String getEndpoint() { return endpoint; }
        /** 设置 OBS endpoint。 */
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        /** 获取 OBS 地域。 */
        public String getRegion() { return region; }
        /** 设置 OBS 地域。 */
        public void setRegion(String region) { this.region = region; }
        /** 获取凭证来源模式。 */
        public HuaweiObsCredentialMode getCredentialMode() { return credentialMode; }
        /** 设置凭证来源模式。 */
        public void setCredentialMode(HuaweiObsCredentialMode credentialMode) {
            this.credentialMode = credentialMode;
        }
        /** 获取访问密钥。 */
        public String getAccessKey() { return accessKey; }
        /** 设置访问密钥。 */
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        /** 获取私密访问密钥。 */
        public String getSecretKey() { return secretKey; }
        /** 设置私密访问密钥。 */
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        /** 获取 STS 安全令牌。 */
        public String getSecurityToken() { return securityToken; }
        /** 设置 STS 安全令牌。 */
        public void setSecurityToken(String securityToken) { this.securityToken = securityToken; }
        /** 获取连接超时。 */
        public Duration getConnectTimeout() { return connectTimeout; }
        /** 设置连接超时。 */
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        /** 获取读取超时。 */
        public Duration getReadTimeout() { return readTimeout; }
        /** 设置读取超时。 */
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        /** 获取写入超时。 */
        public Duration getWriteTimeout() { return writeTimeout; }
        /** 设置写入超时。 */
        public void setWriteTimeout(Duration writeTimeout) { this.writeTimeout = writeTimeout; }
    }

    /**
     * 普通上传与预签名上传共同使用的限制属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Upload {
        /** 默认最大上传大小。 */
        private long maxSize = UploadRequest.DEFAULT_MAX_SIZE_BYTES;
        /** 允许的内容类型白名单。 */
        private Set<String> contentTypeWhitelist = new LinkedHashSet<>();

        /**
         * 获取最大上传大小。
         *
         * @return 最大上传字节数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public long getMaxSize() {
            return maxSize;
        }

        /**
         * 设置最大上传大小。
         *
         * @param maxSize 最大上传字节数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        /**
         * 获取内容类型白名单。
         *
         * @return 内容类型白名单
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Set<String> getContentTypeWhitelist() {
            return contentTypeWhitelist;
        }

        /**
         * 设置内容类型白名单。
         *
         * @param contentTypeWhitelist 内容类型白名单
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setContentTypeWhitelist(Set<String> contentTypeWhitelist) {
            this.contentTypeWhitelist = contentTypeWhitelist;
        }
    }

    /**
     * 大文件 multipart 上传的资源与会话限制。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public static class Multipart {
        /** S3 与 OSS 对非末分片共同要求的最小大小。 */
        private static final DataSize MIN_SUPPORTED_PART_SIZE = DataSize.ofMegabytes(5L);
        /** 对象存储 provider 共同支持的最大分片数。 */
        private static final int MAX_SUPPORTED_PARTS = 10_000;
        /** 默认分片大小，满足 S3 与 OSS 的最小分片约束。 */
        private DataSize partSize = DataSize.ofMegabytes(10L);
        /** 对象存储 provider 共同支持的最大分片数。 */
        private int maxParts = MAX_SUPPORTED_PARTS;
        /** 未完成会话的最长保留时间。 */
        private Duration sessionTtl = Duration.ofHours(24L);
        /** Local provider 未完成分片的独立临时目录。 */
        private Path localRoot = Paths.get(System.getProperty("java.io.tmpdir"),
                "dinghai-file-storage-multipart");
        /** 应用代理 Local 分片写入时允许的最大并发数。 */
        private int maxConcurrentUploads = 8;

        /**
         * 获取单片大小。
         *
         * @return 单片大小
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public DataSize getPartSize() { return partSize; }
        /**
         * 设置单片大小。
         *
         * @param partSize 单片大小
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public void setPartSize(DataSize partSize) { this.partSize = partSize; }
        /**
         * 获取最大分片数。
         *
         * @return 最大分片数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public int getMaxParts() { return maxParts; }
        /**
         * 设置最大分片数。
         *
         * @param maxParts 最大分片数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public void setMaxParts(int maxParts) { this.maxParts = maxParts; }
        /**
         * 获取未完成会话保留时间。
         *
         * @return 未完成会话保留时间
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public Duration getSessionTtl() { return sessionTtl; }
        /**
         * 设置未完成会话保留时间。
         *
         * @param sessionTtl 未完成会话保留时间
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
        /**
         * 获取 Local 未完成分片根目录。
         *
         * @return Local 未完成分片根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public Path getLocalRoot() { return localRoot; }
        /**
         * 设置 Local 未完成分片根目录。
         *
         * @param localRoot Local 未完成分片根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public void setLocalRoot(Path localRoot) { this.localRoot = localRoot; }
        /**
         * 获取 Local 分片上传最大并发数。
         *
         * @return Local 分片上传最大并发数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
        /**
         * 设置 Local 分片上传最大并发数。
         *
         * @param maxConcurrentUploads Local 分片上传最大并发数
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        public void setMaxConcurrentUploads(int maxConcurrentUploads) {
            this.maxConcurrentUploads = maxConcurrentUploads;
        }

        /**
         * 校验跨 provider 一致的 multipart 资源边界。
         *
         * @throws IllegalArgumentException multipart 配置不满足资源边界时抛出
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        void validate() {
            // 任一资源限制无效时拒绝启动，避免请求到达 provider 后才失败。
            if (partSize == null || partSize.compareTo(MIN_SUPPORTED_PART_SIZE) < 0 || maxParts < 1
                    || maxParts > MAX_SUPPORTED_PARTS
                    || sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()
                    || localRoot == null || maxConcurrentUploads < 1) {
                throw new IllegalArgumentException("multipart configuration is invalid");
            }
        }
    }

    /**
     * 本地 HMAC 预签名属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Security {
        /** 签名密钥。 */
        private String signingKey;
        /** 稳定文件 ID 的活动密钥。 */
        private String stableIdKey;
        /** 最大预签名有效期。 */
        private Duration maximumPresignExpiry = Duration.ofHours(1L);

        /**
         * 获取签名密钥。
         *
         * @return 签名密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getSigningKey() {
            return signingKey;
        }

        /**
         * 设置签名密钥。
         *
         * @param signingKey 签名密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setSigningKey(String signingKey) {
            this.signingKey = signingKey;
        }

        /**
         * 获取稳定文件 ID 的活动密钥。
         *
         * @return 稳定文件 ID 密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getStableIdKey() {
            return stableIdKey;
        }

        /**
         * 设置稳定文件 ID 的活动密钥。
         *
         * @param stableIdKey 稳定文件 ID 密钥
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setStableIdKey(String stableIdKey) {
            this.stableIdKey = stableIdKey;
        }

        /**
         * 获取最大预签名有效期。
         *
         * @return 最大预签名有效期
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public Duration getMaximumPresignExpiry() {
            return maximumPresignExpiry;
        }

        /**
         * 设置最大预签名有效期。
         *
         * @param maximumPresignExpiry 最大预签名有效期
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setMaximumPresignExpiry(Duration maximumPresignExpiry) {
            this.maximumPresignExpiry = maximumPresignExpiry;
        }
    }

    /**
     * Starter Web 访问属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Web {
        /** 是否注册 Web 端点。 */
        private boolean enabled = true;
        /** 文件端点路径前缀。 */
        private String pathPrefix = "/files";
        /** 上传端点相对于前缀的路径。 */
        private String uploadPath = "/upload";
        /** 下载端点相对于前缀的路径。 */
        private String downloadPath = "/download";
        /** 预览端点相对于前缀的路径。 */
        private String previewPath = "/preview";
        /** 真实对象 key 读取端点相对于前缀的路径。 */
        private String readPath = "/read/key";
        /** 稳定文件 ID 读取端点相对于前缀的路径。 */
        private String stableReadPath = "/read";
        /** 是否注册历史对象 key 直读端点。 */
        private boolean legacyReadEnabled;
        /** 历史对象 key 直读端点相对于前缀的路径。 */
        private String legacyReadPath = "/";
        /** 是否允许没有预签名凭据的直接访问。 */
        private boolean directAccess;
        /** 是否允许标准二进制类型在接受压缩时使用 sendfile。 */
        private boolean sendfileBinaryContentTypesEnabled = true;
        /** 单个响应的读取缓冲区大小。 */
        private DataSize readBufferSize = DataSize.ofKilobytes(128L);
        /** 同时执行的最大响应读取数。 */
        private int maxConcurrentReads = 128;
        /** 同时执行的最大 Web 上传数。 */
        private int maxConcurrentUploads = 16;
        /** 单个宿主限流 key 的最大上传或读取并发数，零表示关闭。 */
        private int maxConcurrentTransfersPerKey;

        /**
         * 判断是否注册 Web 端点。
         *
         * @return 注册时返回 true
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否注册 Web 端点。
         *
         * @param enabled 是否注册
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取文件端点路径前缀。
         *
         * @return 路径前缀
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public String getPathPrefix() {
            return pathPrefix;
        }

        /**
         * 设置文件端点路径前缀。
         *
         * @param pathPrefix 路径前缀
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        /**
         * 获取上传端点相对于前缀的路径。
         *
         * @return 上传端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getUploadPath() {
            return uploadPath;
        }

        /**
         * 设置上传端点相对于前缀的路径。
         *
         * @param uploadPath 上传端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setUploadPath(String uploadPath) {
            this.uploadPath = uploadPath;
        }

        /**
         * 获取下载端点相对于前缀的路径。
         *
         * @return 下载端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getDownloadPath() {
            return downloadPath;
        }

        /**
         * 设置下载端点相对于前缀的路径。
         *
         * @param downloadPath 下载端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setDownloadPath(String downloadPath) {
            this.downloadPath = downloadPath;
        }

        /**
         * 获取预览端点相对于前缀的路径。
         *
         * @return 预览端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getPreviewPath() {
            return previewPath;
        }

        /**
         * 设置预览端点相对于前缀的路径。
         *
         * @param previewPath 预览端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setPreviewPath(String previewPath) {
            this.previewPath = previewPath;
        }

        /**
         * 获取真实对象 key 读取端点相对于前缀的路径。
         *
         * @return 真实对象 key 读取端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getReadPath() {
            return readPath;
        }

        /**
         * 设置真实对象 key 读取端点相对于前缀的路径。
         *
         * @param readPath 真实对象 key 读取端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setReadPath(String readPath) {
            this.readPath = readPath;
        }

        /**
         * 获取稳定文件 ID 读取端点相对于前缀的路径。
         *
         * @return 稳定文件 ID 读取端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getStableReadPath() {
            return stableReadPath;
        }

        /**
         * 设置稳定文件 ID 读取端点相对于前缀的路径。
         *
         * @param stableReadPath 稳定文件 ID 读取端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setStableReadPath(String stableReadPath) {
            this.stableReadPath = stableReadPath;
        }

        /**
         * 判断是否启用历史对象 key 直读端点。
         *
         * @return 启用时返回 true
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public boolean isLegacyReadEnabled() {
            return legacyReadEnabled;
        }

        /**
         * 设置是否启用历史对象 key 直读端点。
         *
         * @param legacyReadEnabled 是否启用
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setLegacyReadEnabled(boolean legacyReadEnabled) {
            this.legacyReadEnabled = legacyReadEnabled;
        }

        /**
         * 获取历史对象 key 直读端点相对于前缀的路径。
         *
         * @return 历史直读端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public String getLegacyReadPath() {
            return legacyReadPath;
        }

        /**
         * 设置历史对象 key 直读端点相对于前缀的路径。
         *
         * @param legacyReadPath 历史直读端点路径
         * @author lvdaxianer@yeah.net
         * @date 2026-09-23
         */
        public void setLegacyReadPath(String legacyReadPath) {
            this.legacyReadPath = legacyReadPath;
        }

        /**
         * 判断是否允许直接访问。
         *
         * @return 允许时返回 true
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public boolean isDirectAccess() {
            return directAccess;
        }

        /**
         * 设置是否允许直接访问。
         *
         * @param directAccess 是否允许直接访问
         * @author lvdaxianer@yeah.net
         * @date 2026-09-11
         */
        public void setDirectAccess(boolean directAccess) {
            this.directAccess = directAccess;
        }

        /** 获取标准二进制 sendfile 优化开关。 */
        public boolean isSendfileBinaryContentTypesEnabled() {
            return sendfileBinaryContentTypesEnabled;
        }

        /** 设置标准二进制 sendfile 优化开关。 */
        public void setSendfileBinaryContentTypesEnabled(boolean sendfileBinaryContentTypesEnabled) {
            this.sendfileBinaryContentTypesEnabled = sendfileBinaryContentTypesEnabled;
        }

        /** 获取单个响应的读取缓冲区大小。 */
        public DataSize getReadBufferSize() { return readBufferSize; }
        /** 设置单个响应的读取缓冲区大小。 */
        public void setReadBufferSize(DataSize readBufferSize) { this.readBufferSize = readBufferSize; }
        /** 获取同时执行的最大响应读取数。 */
        public int getMaxConcurrentReads() { return maxConcurrentReads; }
        /** 设置同时执行的最大响应读取数。 */
        public void setMaxConcurrentReads(int maxConcurrentReads) { this.maxConcurrentReads = maxConcurrentReads; }
        /** 获取同时执行的最大 Web 上传数。 */
        public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
        /** 设置同时执行的最大 Web 上传数。 */
        public void setMaxConcurrentUploads(int maxConcurrentUploads) { this.maxConcurrentUploads = maxConcurrentUploads; }
        /** 获取单个宿主限流 key 的最大上传或读取并发数。 */
        public int getMaxConcurrentTransfersPerKey() { return maxConcurrentTransfersPerKey; }
        /** 设置单个宿主限流 key 的最大上传或读取并发数。 */
        public void setMaxConcurrentTransfersPerKey(int maxConcurrentTransfersPerKey) {
            this.maxConcurrentTransfersPerKey = maxConcurrentTransfersPerKey;
        }
    }

    /**
     * 非本地 provider 的二级磁盘缓存属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public static class Cache {
        /** 非本地 provider 默认启用。 */
        private boolean enabled = true;
        /** 默认落在系统临时目录下的组件专属缓存根。 */
        private Path root = Paths.get(System.getProperty("java.io.tmpdir"), "dinghai-file-storage-cache");
        /** 允许异步落盘的最大对象大小。 */
        private long maxObjectSize = 50L * 1024L * 1024L;
        /** 空闲缓存副本的保留时间。 */
        private Duration idleTtl = Duration.ofDays(7L);
        /** 缓存填充复制缓冲区大小。 */
        private DataSize bufferSize = DataSize.ofKilobytes(128L);
        /** 同时执行的缓存填充数。 */
        private int maxConcurrentFills = 4;
        /** 缓存填充等待队列容量。 */
        private int queueCapacity = 32;

        /** 获取是否启用缓存。 */
        public boolean isEnabled() { return enabled; }
        /** 设置是否启用缓存。 */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        /** 获取缓存根目录。 */
        public Path getRoot() { return root; }
        /** 设置缓存根目录。 */
        public void setRoot(Path root) { this.root = root; }
        /** 获取异步落盘的最大对象大小。 */
        public long getMaxObjectSize() { return maxObjectSize; }
        /** 设置异步落盘的最大对象大小。 */
        public void setMaxObjectSize(long maxObjectSize) { this.maxObjectSize = maxObjectSize; }
        /** 获取缓存空闲保留时间。 */
        public Duration getIdleTtl() { return idleTtl; }
        /** 设置缓存空闲保留时间。 */
        public void setIdleTtl(Duration idleTtl) { this.idleTtl = idleTtl; }
        /** 获取缓存填充复制缓冲区大小。 */
        public DataSize getBufferSize() { return bufferSize; }
        /** 设置缓存填充复制缓冲区大小。 */
        public void setBufferSize(DataSize bufferSize) { this.bufferSize = bufferSize; }
        /** 获取同时执行的缓存填充数。 */
        public int getMaxConcurrentFills() { return maxConcurrentFills; }
        /** 设置同时执行的缓存填充数。 */
        public void setMaxConcurrentFills(int maxConcurrentFills) { this.maxConcurrentFills = maxConcurrentFills; }
        /** 获取缓存填充等待队列容量。 */
        public int getQueueCapacity() { return queueCapacity; }
        /** 设置缓存填充等待队列容量。 */
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    /**
     * 服务器目录迁移包根目录配置。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public static class Migration {
        /** 是否启用服务器目录迁移服务和管理接口。 */
        private boolean enabled;
        /** 导出包根目录。 */
        private Path exportRoot;
        /** 待导入包根目录。 */
        private Path importRoot;
        /** 导入成功包根目录。 */
        private Path completedRoot;
        /** 导入失败包根目录。 */
        private Path failedRoot;

        /**
         * 判断是否启用迁移。
         *
         * @return 启用时返回 true
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用迁移。
         *
         * @param enabled 是否启用迁移
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取导出包根目录。
         *
         * @return 导出包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public Path getExportRoot() {
            return exportRoot;
        }

        /**
         * 设置导出包根目录。
         *
         * @param exportRoot 导出包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public void setExportRoot(Path exportRoot) {
            this.exportRoot = exportRoot;
        }

        /**
         * 获取待导入包根目录。
         *
         * @return 待导入包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public Path getImportRoot() {
            return importRoot;
        }

        /**
         * 设置待导入包根目录。
         *
         * @param importRoot 待导入包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public void setImportRoot(Path importRoot) {
            this.importRoot = importRoot;
        }

        /**
         * 获取导入成功包根目录。
         *
         * @return 导入成功包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public Path getCompletedRoot() {
            return completedRoot;
        }

        /**
         * 设置导入成功包根目录。
         *
         * @param completedRoot 导入成功包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public void setCompletedRoot(Path completedRoot) {
            this.completedRoot = completedRoot;
        }

        /**
         * 获取导入失败包根目录。
         *
         * @return 导入失败包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public Path getFailedRoot() {
            return failedRoot;
        }

        /**
         * 设置导入失败包根目录。
         *
         * @param failedRoot 导入失败包根目录
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        public void setFailedRoot(Path failedRoot) {
            this.failedRoot = failedRoot;
        }
    }
}
