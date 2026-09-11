package io.github.lvdaxianer.dinghai.filestorage.spring;

/**
 * 需要宿主应用授权的直接文件访问操作。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public enum FileStorageAccessOperation {
    /** 上传对象。 */
    UPLOAD,
    /** 下载对象。 */
    DOWNLOAD,
    /** 在线预览对象。 */
    PREVIEW
}
