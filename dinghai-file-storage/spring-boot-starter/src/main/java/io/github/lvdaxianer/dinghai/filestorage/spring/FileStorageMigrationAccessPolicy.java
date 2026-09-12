package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.security.Principal;

/**
 * 由宿主应用实现的离线迁移管理操作授权扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
@FunctionalInterface
public interface FileStorageMigrationAccessPolicy {

    /**
     * 判断当前认证主体是否可执行离线迁移管理操作。
     *
     * @param principal 当前认证主体
     * @param operation 请求的迁移管理操作
     * @return 允许时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    boolean isAllowed(Principal principal, FileStorageMigrationOperation operation);
}
