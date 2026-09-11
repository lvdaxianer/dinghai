package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MinIO 适配器的最小契约测试。 */
public class MinioFileStorageAdapterTest {

    /** MinIO 模块提供后端中立的文件存储适配器入口。 */
    @Test
    public void exposesMinioFileStorageAdapter() throws Exception {
        assertNotNull(Class.forName(
                "io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter"));
    }

    /** 配置缺少端点时快速失败。 */
    @Test
    public void rejectsIncompleteConfiguration() {
        try {
            new MinioStorageConfiguration("", "bucket", "access", "secret");
            fail("expected configuration validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** 网络超时必须是正值。 */
    @Test
    public void rejectsNonPositiveTimeout() {
        try {
            new MinioStorageConfiguration("https://minio.example", "bucket", "access", "secret",
                    Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1));
            fail("expected timeout validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** 上传 POST policy 绑定对象 key、内容类型、长度和缓存策略。 */
    @Test
    public void createsConstrainedPostPolicy() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.getPresignedPostFormData(any(PostPolicy.class))).thenReturn(java.util.Map.of(
                "key", "用户/头像.png", "Content-Type", "image/png",
                "Cache-Control", "public, max-age=60", "x-amz-signature", "signature"));
        MinioFileStorageAdapter adapter = adapter(client);

        PresignedUpload result = adapter.presignUpload(new PresignUploadRequest(
                ObjectKey.of("用户/头像.png"), Duration.ofMinutes(5), "image/png", 1024,
                new CacheControl("public, max-age=60")));

        assertEquals("POST", result.method());
        assertEquals("public, max-age=60", result.requiredHeaders().get("Cache-Control"));
        assertEquals("用户/头像.png", result.formFields().get("key"));
        assertEquals("image/png", result.formFields().get("Content-Type"));
        assertTrue(result.formFields().containsKey("x-amz-signature"));
        verify(client).getPresignedPostFormData(any(PostPolicy.class));
    }

    /** 下载 URL 绑定 GET、响应处置和缓存策略。 */
    @Test
    public void createsConstrainedDownloadUrl() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example/url");
        MinioFileStorageAdapter adapter = adapter(client);

        PresignedDownload result = adapter.presignDownload(new PresignDownloadRequest(
                ObjectKey.of("reports/a.pdf"), Duration.ofMinutes(5),
                PresignDownloadRequest.ResponseDisposition.INLINE,
                new CacheControl("private, max-age=30")));

        assertEquals("GET", result.method());
        assertEquals("https://minio.example/url", result.url());
        ArgumentCaptor<GetPresignedObjectUrlArgs> arguments = ArgumentCaptor.forClass(
                GetPresignedObjectUrlArgs.class);
        verify(client).getPresignedObjectUrl(arguments.capture());
        assertEquals(io.minio.http.Method.GET, arguments.getValue().method());
    }

    private static MinioFileStorageAdapter adapter() {
        return new MinioFileStorageAdapter(new MinioStorageConfiguration(
                "https://minio.example", "uploads", "test-access", "test-secret"));
    }

    private static MinioFileStorageAdapter adapter(MinioClient client) {
        return new MinioFileStorageAdapter(client, "uploads", "https://minio.example");
    }
}
