package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RustFS 适配器的最小后端中立契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
public class RustfsFileStorageAdapterTest {

    /**
     * RustFS 模块必须暴露独立的适配器入口。
     *
     * @throws Exception 类解析失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void exposesRustfsFileStorageAdapter() throws Exception {
        assertNotNull(Class.forName(
                "io.github.lvdaxianer.dinghai.filestorage.rustfs.RustfsFileStorageAdapter"));
    }

    /**
     * 必填连接项缺失必须在配置构建阶段失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void rejectsIncompleteConfiguration() {
        try {
            new RustfsStorageConfiguration("", "uploads", "access", "secret");
            fail("expected configuration validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /**
     * 非正网络超时必须被拒绝，避免不可控的远程请求。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void rejectsNonPositiveTimeout() {
        try {
            new RustfsStorageConfiguration("https://rustfs.example", "uploads", "access",
                    "secret", Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1));
            fail("expected timeout validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /**
     * 对象生命周期方法必须继续使用统一存储契约。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void forwardsObjectLifecycleOperations() {
        FileStorage delegate = mock(FileStorage.class);
        ObjectKey key = ObjectKey.of("reports/monthly.pdf");
        stubLifecycle(delegate, key);
        RustfsFileStorageAdapter adapter = new RustfsFileStorageAdapter(delegate);

        invokeLifecycle(adapter, key);
        verifyLifecycle(delegate, key);
    }

    /**
     * RustFS 适配器必须转发原生 S3 预签名上传和下载结果。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void forwardsNativePresigning() {
        FileStorage delegate = mock(FileStorage.class);
        PresignedUpload upload = upload();
        PresignedDownload download = download();
        PresignUploadRequest uploadRequest = uploadRequest();
        PresignDownloadRequest downloadRequest = downloadRequest();
        when(delegate.presignUpload(uploadRequest)).thenReturn(upload);
        when(delegate.presignDownload(downloadRequest)).thenReturn(download);
        RustfsFileStorageAdapter adapter = new RustfsFileStorageAdapter(delegate);

        PresignedUpload actualUpload = adapter.presignUpload(uploadRequest);
        PresignedDownload actualDownload = adapter.presignDownload(downloadRequest);

        assertEquals(upload, actualUpload);
        assertEquals(download, actualDownload);
        verify(delegate).presignUpload(uploadRequest);
        verify(delegate).presignDownload(downloadRequest);
    }

    /**
     * RustFS 对外错误不得沿用其他供应商名称或泄露底层信息。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void mapsDelegateFailuresToRustfsError() {
        FileStorage delegate = mock(FileStorage.class);
        ObjectKey key = ObjectKey.of("private/object.txt");
        when(delegate.exists(key)).thenThrow(new FileStorageException(
                FileStorageErrorCode.AUTHENTICATION_FAILED, "MinIO operation failed"));
        RustfsFileStorageAdapter adapter = new RustfsFileStorageAdapter(delegate);

        try {
            adapter.exists(key);
            fail("expected mapped storage failure");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.AUTHENTICATION_FAILED, expected.errorCode());
            assertEquals("RustFS operation failed", expected.getMessage());
        }
    }

    /**
     * 创建供预签名转发断言使用的上传请求。
     *
     * @return 有效上传请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static PresignUploadRequest uploadRequest() {
        return new PresignUploadRequest(ObjectKey.of("reports/monthly.pdf"),
                Duration.ofMinutes(5), "application/pdf", 1024);
    }

    /**
     * 创建供预签名转发断言使用的下载请求。
     *
     * @return 有效下载请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static PresignDownloadRequest downloadRequest() {
        return new PresignDownloadRequest(ObjectKey.of("reports/monthly.pdf"),
                Duration.ofMinutes(5), PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /**
     * 创建供预签名转发断言使用的上传结果。
     *
     * @return 有效上传结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static PresignedUpload upload() {
        return new PresignedUpload("https://rustfs.example/uploads", "POST", Instant.EPOCH,
                Map.of(), Map.of());
    }

    /**
     * 创建供预签名转发断言使用的下载结果。
     *
     * @return 有效下载结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static PresignedDownload download() {
        return new PresignedDownload("https://rustfs.example/download", "GET", Instant.EPOCH,
                PresignDownloadRequest.ResponseDisposition.INLINE, Map.of());
    }

    /**
     * 配置对象生命周期委托的稳定返回值。
     *
     * @param delegate 后端中立存储委托
     * @param key 已校验对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static void stubLifecycle(FileStorage delegate, ObjectKey key) {
        when(delegate.get(key)).thenReturn(Optional.empty());
        when(delegate.stat(key)).thenReturn(Optional.empty());
        when(delegate.exists(key)).thenReturn(false);
        when(delegate.delete(key)).thenReturn(false);
        when(delegate.list()).thenReturn(Stream.empty());
    }

    /**
     * 调用全部无写入的对象生命周期操作。
     *
     * @param adapter RustFS 存储适配器
     * @param key 已校验对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static void invokeLifecycle(RustfsFileStorageAdapter adapter, ObjectKey key) {
        adapter.get(key);
        adapter.stat(key);
        adapter.exists(key);
        adapter.delete(key);
        adapter.list().close();
    }

    /**
     * 验证每个无写入生命周期调用均交给后端委托。
     *
     * @param delegate 后端中立存储委托
     * @param key 已校验对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static void verifyLifecycle(FileStorage delegate, ObjectKey key) {
        verify(delegate).get(key);
        verify(delegate).stat(key);
        verify(delegate).exists(key);
        verify(delegate).delete(key);
        verify(delegate).list();
    }
}
