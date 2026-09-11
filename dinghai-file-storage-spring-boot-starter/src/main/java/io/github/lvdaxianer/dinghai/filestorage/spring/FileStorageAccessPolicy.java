package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.security.Principal;

/**
 * 由宿主应用实现的直接文件访问授权扩展点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
@FunctionalInterface
public interface FileStorageAccessPolicy {

    /**
     * 判断当前认证主体是否可以直接执行对象操作。
     *
     * @param principal 当前认证主体
     * @param key 已验证对象 key
     * @param operation 请求的文件操作
     * @return 允许时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    boolean isAllowed(Principal principal, ObjectKey key, FileStorageAccessOperation operation);
}
