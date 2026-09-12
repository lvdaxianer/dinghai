package io.github.lvdaxianer.dinghai.filestorage.spring;

/**
 * 需要宿主应用授权的离线迁移管理操作。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
public enum FileStorageMigrationOperation {
    /** 将当前存储导出为迁移包。 */
    EXPORT,
    /** 将服务器目录中的迁移包导入当前存储。 */
    IMPORT
}
