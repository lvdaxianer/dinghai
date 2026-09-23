package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.List;

/**
 * 文件存储 Web 端点的已校验完整路由集合。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-23
 */
final class FileStorageWebRoutes {

    /** Spring MVC 的多段对象 key 路径变量。 */
    private static final String KEY_VARIABLE = "/{*key}";
    /** Spring MVC 的单段稳定 ID 路径变量。 */
    private static final String IDENTIFIER_VARIABLE = "/{identifier}";
    /** 稳定 ID 的显式版本段。 */
    private static final String VERSION_PATH = "/v1";
    /** 预览操作路径段。 */
    private static final String PREVIEW_PATH = "/preview";
    /** 下载操作路径段。 */
    private static final String DOWNLOAD_PATH = "/download";

    private final String pathPrefix;
    private final String uploadPath;
    private final String downloadPath;
    private final String previewPath;
    private final String readPath;
    private final String stableReadPath;
    private final boolean legacyReadEnabled;
    private final String legacyReadPath;

    private FileStorageWebRoutes(String pathPrefix, String uploadPath, String downloadPath,
                                 String previewPath, String readPath, String stableReadPath,
                                 boolean legacyReadEnabled, String legacyReadPath) {
        this.pathPrefix = pathPrefix;
        this.uploadPath = uploadPath;
        this.downloadPath = downloadPath;
        this.previewPath = previewPath;
        this.readPath = readPath;
        this.stableReadPath = stableReadPath;
        this.legacyReadEnabled = legacyReadEnabled;
        this.legacyReadPath = legacyReadPath;
        validateConflicts();
    }

    /**
     * 将绑定的 Web 属性转换为可注册的完整路由。
     *
     * @param web 已绑定的 Web 属性
     * @return 已校验的完整路由
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    static FileStorageWebRoutes from(FileStorageProperties.Web web) {
        if (web == null) {
            throw invalidConfiguration();
        }
        return new FileStorageWebRoutes(normalize(web.getPathPrefix()), normalize(web.getUploadPath()),
                normalize(web.getDownloadPath()), normalize(web.getPreviewPath()),
                normalize(web.getReadPath()), normalize(web.getStableReadPath()),
                web.isLegacyReadEnabled(), normalize(web.getLegacyReadPath()));
    }

    /**
     * 将规范化后的路径写回绑定属性，供依赖 Web 前缀的存储适配器复用。
     *
     * @param web 已绑定的 Web 属性
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    void applyTo(FileStorageProperties.Web web) {
        web.setPathPrefix(propertyPath(pathPrefix));
        web.setUploadPath(propertyPath(uploadPath));
        web.setDownloadPath(propertyPath(downloadPath));
        web.setPreviewPath(propertyPath(previewPath));
        web.setReadPath(propertyPath(readPath));
        web.setStableReadPath(propertyPath(stableReadPath));
        web.setLegacyReadPath(propertyPath(legacyReadPath));
    }

    /**
     * 返回上传对象的完整映射路径。
     *
     * @return 上传映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String upload() {
        return objectRoute(uploadPath);
    }

    /**
     * 返回上传对象 URL 的无变量基础路径。
     *
     * @return 上传 URL 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String uploadBasePath() {
        return join(uploadPath);
    }

    /**
     * 返回下载对象的完整映射路径。
     *
     * @return 下载映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String download() {
        return objectRoute(downloadPath);
    }

    /**
     * 返回下载对象 URL 的无变量基础路径。
     *
     * @return 下载 URL 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String downloadBasePath() {
        return join(downloadPath);
    }

    /**
     * 返回预览对象的完整映射路径。
     *
     * @return 预览映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String preview() {
        return objectRoute(previewPath);
    }

    /**
     * 返回预览对象 URL 的无变量基础路径。
     *
     * @return 预览 URL 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String previewBasePath() {
        return join(previewPath);
    }

    /**
     * 返回真实对象 key 读取的完整映射路径。
     *
     * @return 真实 key 读取映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String keyRead() {
        return objectRoute(readPath);
    }

    /**
     * 返回真实对象 key 预览的完整映射路径。
     *
     * @return 真实 key 预览映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String keyPreview() {
        return objectRoute(readPath + PREVIEW_PATH);
    }

    /**
     * 返回真实对象 key 下载的完整映射路径。
     *
     * @return 真实 key 下载映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String keyDownload() {
        return objectRoute(readPath + DOWNLOAD_PATH);
    }

    /**
     * 返回稳定文件 ID 的非版本读取映射路径。
     *
     * @return 稳定 ID 读取映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stableRead() {
        return join(stableReadPath) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回稳定文件 ID 的版本读取映射路径。
     *
     * @return 版本稳定 ID 读取映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stableVersionedRead() {
        return join(stableReadPath + VERSION_PATH) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回稳定文件 ID 的非版本预览映射路径。
     *
     * @return 稳定 ID 预览映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stablePreview() {
        return join(stableReadPath + PREVIEW_PATH) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回稳定文件 ID 的版本预览映射路径。
     *
     * @return 版本稳定 ID 预览映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stableVersionedPreview() {
        return join(stableReadPath + VERSION_PATH + PREVIEW_PATH) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回稳定文件 ID 的非版本下载映射路径。
     *
     * @return 稳定 ID 下载映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stableDownload() {
        return join(stableReadPath + DOWNLOAD_PATH) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回稳定文件 ID 的版本下载映射路径。
     *
     * @return 版本稳定 ID 下载映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String stableVersionedDownload() {
        return join(stableReadPath + VERSION_PATH + DOWNLOAD_PATH) + IDENTIFIER_VARIABLE;
    }

    /**
     * 返回可选历史对象 key 读取映射路径。
     *
     * @return 未启用时返回 null，否则返回历史读取映射路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String legacyRead() {
        return legacyReadEnabled ? objectRoute(legacyReadPath) : null;
    }

    /**
     * 判断非版本稳定 ID 路由是否会被真实 key catch-all 覆盖。
     *
     * @return 需要仅注册显式版本稳定路由时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    boolean suppressUnversionedStableRoutes() {
        return stableReadPath.equals(readPath) || stableReadPath.startsWith(readPath + "/");
    }

    /**
     * 返回对象 key 预览 URL 的无变量基础路径。
     *
     * @return 已规范化的预览 URL 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    String keyPreviewBasePath() {
        return join(readPath + PREVIEW_PATH) + "/";
    }

    /**
     * 将相对路径组合为带对象 key 变量的映射。
     *
     * @param path 相对于前缀的路径
     * @return 完整对象 key 映射
     */
    private String objectRoute(String path) {
        return join(path) + KEY_VARIABLE;
    }

    /**
     * 将已经规范化的前缀与子路径组合为完整路径。
     *
     * @param path 相对于前缀的路径
     * @return 完整基础路径
     */
    private String join(String path) {
        return pathPrefix + path;
    }

    /**
     * 将输入路径转换为无重复或尾随分隔符的片段。
     *
     * @param raw 原始配置路径
     * @return 已规范化路径，根路径返回空字符串
     */
    private static String normalize(String raw) {
        if (raw == null || containsUnsafeCharacter(raw)) {
            throw invalidConfiguration();
        }
        String value = raw.replaceAll("/{2,}", "/");
        String leading = value.startsWith("/") ? value : "/" + value;
        return trimTrailingSeparator(leading);
    }

    /**
     * 判断路径中是否存在不得出现在 URI path 的字符。
     *
     * @param value 原始配置路径
     * @return 包含非法字符时返回 true
     */
    private static boolean containsUnsafeCharacter(String value) {
        return value.indexOf('?') >= 0 || value.indexOf('#') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    /**
     * 移除尾随分隔符，并将根路径统一表示为无子路径。
     *
     * @param value 已有前导分隔符的路径
     * @return 无尾随分隔符的路径
     */
    private static String trimTrailingSeparator(String value) {
        String trimmed = value;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return "/".equals(trimmed) ? "" : trimmed;
    }

    /**
     * 将内部的根路径表示转换为属性可读的根路径值。
     *
     * @param path 已规范化的内部路径
     * @return 可写回属性的路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    private static String propertyPath(String path) {
        return path.isEmpty() ? "/" : path;
    }

    /**
     * 校验会产生相同对象 key 映射的配置组合。
     */
    private void validateConflicts() {
        List<String> routes = List.of(upload(), download(), preview(), keyRead(), keyPreview(), keyDownload());
        if (routes.stream().distinct().count() != routes.size()) {
            throw invalidConfiguration();
        }
        if (legacyReadEnabled && routes.contains(legacyRead())) {
            throw invalidConfiguration();
        }
    }

    /**
     * 创建不泄漏配置值的稳定配置错误。
     *
     * @return 无效配置异常
     */
    private static FileStorageException invalidConfiguration() {
        return new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                "file storage web route configuration is invalid");
    }
}
