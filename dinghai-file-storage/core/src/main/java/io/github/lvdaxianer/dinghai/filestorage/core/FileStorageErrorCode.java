package io.github.lvdaxianer.dinghai.filestorage.core;

/**
 * 文件存储稳定错误分类。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public enum FileStorageErrorCode {
    /** 请求参数非法。 */
    INVALID_REQUEST,
    /** 对象不存在。 */
    OBJECT_NOT_FOUND,
    /** 对象 key 非法。 */
    INVALID_OBJECT_KEY,
    /** 文件超过限制。 */
    OBJECT_TOO_LARGE,
    /** 内容类型不允许。 */
    CONTENT_TYPE_NOT_ALLOWED,
    /** 后端认证失败。 */
    AUTHENTICATION_FAILED,
    /** 后端服务失败。 */
    STORAGE_UNAVAILABLE,
    /** 签名无效或已过期。 */
    INVALID_PRESIGN,
    /** 配置非法。 */
    INVALID_CONFIGURATION,
    /** Web 资源并发达到上限。 */
    RATE_LIMITED,
    /** 未分类故障。 */
    INTERNAL_ERROR
}
