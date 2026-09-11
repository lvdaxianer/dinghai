package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import java.security.Principal;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件存储直接访问授权扩展点测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageAccessPolicyTest {

    /** 测试用认证主体。 */
    private static final Principal PRINCIPAL = () -> "operator";

    /**
     * 授权策略应能区分下载、预览与上传操作。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldDelegateEachAccessOperationToApplicationPolicy() {
        FileStorageAccessPolicy policy = (principal, key, operation) -> {
            return PRINCIPAL.equals(principal)
                    && ObjectKey.of("documents/report.pdf").equals(key)
                    && operation != FileStorageAccessOperation.UPLOAD;
        };

        ObjectKey key = ObjectKey.of("documents/report.pdf");
        assertThat(policy.isAllowed(PRINCIPAL, key, FileStorageAccessOperation.DOWNLOAD)).isTrue();
        assertThat(policy.isAllowed(PRINCIPAL, key, FileStorageAccessOperation.PREVIEW)).isTrue();
        assertThat(policy.isAllowed(PRINCIPAL, key, FileStorageAccessOperation.UPLOAD)).isFalse();
    }

    /**
     * 默认访问决策器没有策略时必须拒绝直接访问。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldDenyDirectAccessWhenNoPolicyIsRegistered() {
        FileStorageDirectAccessAuthorizer authorizer = new FileStorageDirectAccessAuthorizer(false,
                null);

        assertThat(authorizer.isAllowed(PRINCIPAL, ObjectKey.of("documents/report.pdf"),
                FileStorageAccessOperation.DOWNLOAD)).isFalse();
    }

    /**
     * 显式开启且策略允许时才可直接下载对象。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldAllowDirectAccessOnlyWhenEnabledAndPolicyAllows() {
        FileStorageAccessPolicy policy = (principal, key, operation) ->
                operation == FileStorageAccessOperation.DOWNLOAD;
        FileStorageDirectAccessAuthorizer authorizer = new FileStorageDirectAccessAuthorizer(true,
                policy);

        assertThat(authorizer.isAllowed(PRINCIPAL, ObjectKey.of("documents/report.pdf"),
                FileStorageAccessOperation.DOWNLOAD)).isTrue();
        assertThat(authorizer.isAllowed(PRINCIPAL, ObjectKey.of("documents/report.pdf"),
                FileStorageAccessOperation.PREVIEW)).isFalse();
        assertThat(authorizer.isAllowed(PRINCIPAL, ObjectKey.of("documents/report.pdf"),
                FileStorageAccessOperation.UPLOAD)).isFalse();
    }
}
