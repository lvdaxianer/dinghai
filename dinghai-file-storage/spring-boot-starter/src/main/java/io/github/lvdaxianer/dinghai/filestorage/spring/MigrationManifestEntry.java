package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;

/**
 * 迁移 manifest 中的单个对象记录。
 *
 * @param version manifest 格式版本
 * @param storageAlias 逻辑存储别名
 * @param key 对象 key
 * @param relativePath 包内对象相对路径
 * @param contentLength 对象长度
 * @param contentType 内容类型
 * @param cacheControl 缓存策略
 * @param sha256 对象 SHA-256 摘要
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record MigrationManifestEntry(int version, String storageAlias, ObjectKey key, String relativePath,
                                     long contentLength, String contentType,
                                     CacheControl cacheControl, String sha256) {
}
