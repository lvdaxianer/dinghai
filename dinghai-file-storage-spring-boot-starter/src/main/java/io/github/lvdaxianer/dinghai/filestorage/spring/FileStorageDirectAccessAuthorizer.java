package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.security.Principal;
import java.util.Optional;

/**
 * 将直接访问开关与宿主授权策略组合为默认拒绝的决策器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
final class FileStorageDirectAccessAuthorizer {

    /** 是否启用直接访问。 */
    private final boolean directAccessEnabled;
    /** 可选宿主授权策略。 */
    private final Optional<FileStorageAccessPolicy> policy;

    /**
     * 创建默认拒绝的直接访问决策器。
     *
     * @param directAccessEnabled 是否显式启用直接访问
     * @param policy 宿主授权策略，可为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    FileStorageDirectAccessAuthorizer(boolean directAccessEnabled,
                                      FileStorageAccessPolicy policy) {
        this.directAccessEnabled = directAccessEnabled;
        this.policy = Optional.ofNullable(policy);
    }

    /**
     * 判断是否允许绕过预签名凭据的直接访问。
     *
     * @param principal 当前认证主体
     * @param key 已验证对象 key
     * @param operation 请求的文件操作
     * @return 开关和授权策略均允许时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    boolean isAllowed(Principal principal, ObjectKey key, FileStorageAccessOperation operation) {
        return directAccessEnabled && policy.map(candidate ->
                candidate.isAllowed(principal, key, operation)).orElse(false);
    }
}
