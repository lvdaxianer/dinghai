package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通过服务器目录包名称触发离线文件迁移。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@RestController
public final class FileStorageMigrationController {
    private final FileStorageMigrationService migrationService;
    private final FileStorageProperties.Migration migration;
    private final FileStorageMigrationAccessPolicy accessPolicy;

    /**
     * 创建迁移接口。
     *
     * @param migrationService 迁移服务
     * @param properties 文件存储配置
     * @param accessPolicy 宿主迁移授权策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public FileStorageMigrationController(FileStorageMigrationService migrationService,
                                          FileStorageProperties properties,
                                          FileStorageMigrationAccessPolicy accessPolicy) {
        this.migrationService = migrationService;
        this.migration = properties.getMigration();
        this.accessPolicy = accessPolicy;
    }

    /**
     * 导出当前 provider 到服务器目录。
     *
     * @param principal 当前认证主体
     * @param packageName 迁移包名称
     * @return 已发布迁移包名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @PostMapping("/files/migration/export")
    public ResponseEntity<String> export(Principal principal,
                                         @RequestParam("packageName") String packageName) {
        requireAccess(principal, FileStorageMigrationOperation.EXPORT);
        return ResponseEntity.ok(migrationService.exportPackage(packageName).getFileName().toString());
    }

    /**
     * 导入服务器目录中的迁移包。
     *
     * @param principal 当前认证主体
     * @param packageName 迁移包名称
     * @return 已完成迁移包名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @PostMapping("/files/migration/import")
    public ResponseEntity<String> importPackage(Principal principal,
                                                @RequestParam("packageName") String packageName) {
        requireAccess(principal, FileStorageMigrationOperation.IMPORT);
        return ResponseEntity.ok(migrationService.importPackage(packageName, migration.getImportRoot(),
                migration.getCompletedRoot(), migration.getFailedRoot()).getFileName().toString());
    }

    /**
     * 调用宿主策略验证迁移管理权限。
     *
     * @param principal 当前认证主体
     * @param operation 请求的迁移操作
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private void requireAccess(Principal principal, FileStorageMigrationOperation operation) {
        if (!accessPolicy.isAllowed(principal, operation)) {
            throw new FileStorageException(FileStorageErrorCode.AUTHENTICATION_FAILED,
                    "file migration access denied");
        }
    }
}
