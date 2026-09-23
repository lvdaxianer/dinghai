package io.github.lvdaxianer.dinghai.filestorage.spring;

/**
 * Web 传输端点的资源限制与宿主限流 key 解析配置。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
final class WebTransferConfiguration {

    /** 单个响应流的复制缓冲区大小。 */
    private final int bufferSize;
    /** 全局最大读取并发数。 */
    private final int maxConcurrentReads;
    /** 全局最大上传并发数。 */
    private final int maxConcurrentUploads;
    /** 单个宿主 key 的最大传输并发数。 */
    private final int maxConcurrentTransfersPerKey;
    /** 宿主可选的传输限流 key 解析器。 */
    private final WebTransferRateLimitKeyResolver keyResolver;
    /** 已校验的 Web 路由。 */
    private final FileStorageWebRoutes routes;
    /** 是否允许标准二进制类型绕过常规压缩回退。 */
    private final boolean sendfileBinaryContentTypesEnabled;

    /**
     * 创建 Web 传输配置。
     *
     * @param bufferSize 单个响应流的复制缓冲区大小
     * @param maxConcurrentReads 全局最大读取并发数
     * @param maxConcurrentUploads 全局最大上传并发数
     * @param maxConcurrentTransfersPerKey 单个宿主 key 的最大传输并发数
     * @param keyResolver 宿主可选的传输限流 key 解析器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    WebTransferConfiguration(int bufferSize, int maxConcurrentReads, int maxConcurrentUploads,
                             int maxConcurrentTransfersPerKey,
                             WebTransferRateLimitKeyResolver keyResolver) {
        this(bufferSize, maxConcurrentReads, maxConcurrentUploads, maxConcurrentTransfersPerKey,
                keyResolver, FileStorageWebRoutes.from(new FileStorageProperties.Web()), true);
    }

    /**
     * 创建带路径前缀的 Web 传输配置。
     *
     * @param bufferSize 单个响应流的复制缓冲区大小
     * @param maxConcurrentReads 全局最大读取并发数
     * @param maxConcurrentUploads 全局最大上传并发数
     * @param maxConcurrentTransfersPerKey 单个宿主 key 的最大传输并发数
     * @param keyResolver 宿主可选的传输限流 key 解析器
     * @param pathPrefix Web 端点路径前缀
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    WebTransferConfiguration(int bufferSize, int maxConcurrentReads, int maxConcurrentUploads,
                             int maxConcurrentTransfersPerKey,
                             WebTransferRateLimitKeyResolver keyResolver, String pathPrefix) {
        this(bufferSize, maxConcurrentReads, maxConcurrentUploads, maxConcurrentTransfersPerKey,
                keyResolver, routesForPathPrefix(pathPrefix), true);
    }

    /**
     * 创建带路径和二进制 sendfile 开关的 Web 传输配置。
     *
     * @param bufferSize 单个响应流的复制缓冲区大小
     * @param maxConcurrentReads 全局最大读取并发数
     * @param maxConcurrentUploads 全局最大上传并发数
     * @param maxConcurrentTransfersPerKey 单个宿主 key 的最大传输并发数
     * @param keyResolver 宿主可选的传输限流 key 解析器
     * @param pathPrefix Web 端点路径前缀
     * @param sendfileBinaryContentTypesEnabled 标准二进制 sendfile 优化开关
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    WebTransferConfiguration(int bufferSize, int maxConcurrentReads, int maxConcurrentUploads,
                             int maxConcurrentTransfersPerKey,
                             WebTransferRateLimitKeyResolver keyResolver, String pathPrefix,
                             boolean sendfileBinaryContentTypesEnabled) {
        this(bufferSize, maxConcurrentReads, maxConcurrentUploads, maxConcurrentTransfersPerKey,
                keyResolver, routesForPathPrefix(pathPrefix), sendfileBinaryContentTypesEnabled);
    }

    /**
     * 创建使用完整路径模型的 Web 传输配置。
     *
     * @param bufferSize 单个响应流的复制缓冲区大小
     * @param maxConcurrentReads 全局最大读取并发数
     * @param maxConcurrentUploads 全局最大上传并发数
     * @param maxConcurrentTransfersPerKey 单个宿主 key 的最大传输并发数
     * @param keyResolver 宿主可选的传输限流 key 解析器
     * @param routes 已校验的 Web 路由
     * @param sendfileBinaryContentTypesEnabled 标准二进制 sendfile 优化开关
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    WebTransferConfiguration(int bufferSize, int maxConcurrentReads, int maxConcurrentUploads,
                             int maxConcurrentTransfersPerKey,
                             WebTransferRateLimitKeyResolver keyResolver, FileStorageWebRoutes routes,
                             boolean sendfileBinaryContentTypesEnabled) {
        this.bufferSize = bufferSize;
        this.maxConcurrentReads = maxConcurrentReads;
        this.maxConcurrentUploads = maxConcurrentUploads;
        this.maxConcurrentTransfersPerKey = maxConcurrentTransfersPerKey;
        this.keyResolver = keyResolver;
        this.routes = routes;
        this.sendfileBinaryContentTypesEnabled = sendfileBinaryContentTypesEnabled;
    }

    /**
     * 返回单个响应流的复制缓冲区大小。
     *
     * @return 缓冲区字节数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    int bufferSize() {
        return bufferSize;
    }

    /**
     * 返回全局最大读取并发数。
     *
     * @return 读取并发上限
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    int maxConcurrentReads() {
        return maxConcurrentReads;
    }

    /**
     * 返回全局最大上传并发数。
     *
     * @return 上传并发上限
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    int maxConcurrentUploads() {
        return maxConcurrentUploads;
    }

    /**
     * 返回单个宿主 key 的最大传输并发数。
     *
     * @return 每 key 并发上限
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    int maxConcurrentTransfersPerKey() {
        return maxConcurrentTransfersPerKey;
    }

    /**
     * 返回宿主可选的传输限流 key 解析器。
     *
     * @return 可为空的宿主解析器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    WebTransferRateLimitKeyResolver keyResolver() {
        return keyResolver;
    }

    /**
     * 返回对象 key 预览 URL 的无变量基础路径。
     *
     * @return 对象 key 预览 URL 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    String keyPreviewBasePath() {
        return routes.keyPreviewBasePath();
    }

    /**
     * 返回已校验的 Web 路由集合。
     *
     * @return Web 路由集合
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    FileStorageWebRoutes routes() {
        return routes;
    }

    /**
     * 返回标准二进制 sendfile 优化开关。
     *
     * @return 启用时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    boolean sendfileBinaryContentTypesEnabled() {
        return sendfileBinaryContentTypesEnabled;
    }

    /**
     * 根据既有构造器的前缀参数创建默认子路径路由。
     *
     * @param pathPrefix Web 端点路径前缀
     * @return 使用默认子路径的已校验路由
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    private static FileStorageWebRoutes routesForPathPrefix(String pathPrefix) {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix(pathPrefix);
        return FileStorageWebRoutes.from(web);
    }
}
