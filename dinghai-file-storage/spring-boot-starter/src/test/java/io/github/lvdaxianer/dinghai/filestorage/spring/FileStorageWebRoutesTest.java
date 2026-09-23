package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 文件 Web 路由配置的启动期规范化测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-23
 */
public class FileStorageWebRoutesTest {

    /**
     * 默认值必须生成历史兼容的端点地址。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldRetainDefaultRoutePaths() {
        FileStorageWebRoutes routes = FileStorageWebRoutes.from(new FileStorageProperties.Web());

        assertEquals("/files/upload/{*key}", routes.upload());
        assertEquals("/files/download/{*key}", routes.download());
        assertEquals("/files/preview/{*key}", routes.preview());
        assertEquals("/files/read/key/{*key}", routes.keyRead());
        assertEquals("/files/read/{identifier}", routes.stableRead());
        assertEquals("/files/read/key/preview/{*key}", routes.keyPreview());
    }

    /**
     * 裸配置值应在路由注册前被规范化。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldBuildCustomizedPathsWithoutRepeatedSeparators() {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix("rsm");
        web.setReadPath("read");
        web.setUploadPath("upload-file");
        web.setDownloadPath("/download-file/");
        web.setPreviewPath("preview-file");
        web.setStableReadPath("stable");
        web.setLegacyReadEnabled(true);
        web.setLegacyReadPath("/");

        FileStorageWebRoutes routes = FileStorageWebRoutes.from(web);

        assertEquals("/rsm/upload-file/{*key}", routes.upload());
        assertEquals("/rsm/download-file/{*key}", routes.download());
        assertEquals("/rsm/preview-file/{*key}", routes.preview());
        assertEquals("/rsm/read/{*key}", routes.keyRead());
        assertEquals("/rsm/{*key}", routes.legacyRead());
        assertEquals("/rsm/read/preview/{*key}", routes.keyPreview());
    }

    /**
     * 空白前缀应表示根路径，并在写回属性时保持为合法根路径值。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldSupportRootPathPrefix() {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix("");

        FileStorageWebRoutes routes = FileStorageWebRoutes.from(web);
        routes.applyTo(web);

        assertEquals("/upload/{*key}", routes.upload());
        assertEquals("/", web.getPathPrefix());
    }

    /**
     * 危险路径值必须在应用启动前以稳定错误码拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldRejectUnsafeRouteConfiguration() {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix("/rsm?debug=true");

        try {
            FileStorageWebRoutes.from(web);
            fail("unsafe routes must be rejected");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.INVALID_CONFIGURATION, exception.errorCode());
        }
    }

    /**
     * 两个同类 key 路由不得在启动后才暴露歧义。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldRejectConflictingObjectKeyPaths() {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setReadPath("/content");
        web.setPreviewPath("content");

        try {
            FileStorageWebRoutes.from(web);
            fail("conflicting routes must be rejected");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.INVALID_CONFIGURATION, exception.errorCode());
        }
    }
}
