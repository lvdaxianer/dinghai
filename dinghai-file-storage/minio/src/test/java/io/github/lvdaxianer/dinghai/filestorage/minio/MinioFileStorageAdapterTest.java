package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        when(client.getPresignedPostFormData(any(PostPolicy.class))).thenReturn(Map.of(
                "x-amz-algorithm", "AWS4-HMAC-SHA256", "x-amz-signature", "signature"));
        MinioFileStorageAdapter adapter = adapter(client);

        PresignedUpload result = adapter.presignUpload(new PresignUploadRequest(
                ObjectKey.of("用户/头像.png"), Duration.ofMinutes(5), "image/png", 1024,
                new CacheControl("public, max-age=60")));

        assertEquals("POST", result.method());
        assertEquals("public, max-age=60", result.requiredHeaders().get("Cache-Control"));
        assertEquals("uploads", result.formFields().get("bucket"));
        assertEquals("用户/头像.png", result.formFields().get("key"));
        assertEquals("image/png", result.formFields().get("Content-Type"));
        assertEquals("public, max-age=60", result.formFields().get("Cache-Control"));
        assertTrue(result.formFields().containsKey("x-amz-signature"));
        verify(client).getPresignedPostFormData(any(PostPolicy.class));
    }

    /** 分片预签名必须使用 S3 UploadPart 的 PUT 查询参数。 */
    @Test
    public void presignsNativeUploadPart() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example/uploads/large.bin?signature");
        MinioFileStorageAdapter adapter = adapter(client);
        MultipartUploadRequest request = new MultipartUploadRequest(ObjectKey.of("large.bin"),
                2_000L, 1_000L, "application/octet-stream", new CacheControl("private"),
                Duration.ofMinutes(5));
        MultipartUploadSession session = new MultipartUploadSession("upload-id", request,
                Instant.now().plusSeconds(300));

        PresignedUpload result = adapter.presignPart(session, new MultipartPartRequest("upload-id", 2, 2));

        assertEquals("PUT", result.method());
        assertTrue(result.url().contains("signature"));
        verify(client).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        assertTrue(adapter instanceof MultipartFileStorage);
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

    /** 枚举时通过对象查询保留完整元数据，并保持递归列举。 */
    @Test
    public void listsCompleteObjectMetadataLazily() throws Exception {
        MinioClient client = mock(MinioClient.class);
        Item item = mock(Item.class);
        StatObjectResponse response = mock(StatObjectResponse.class);
        Instant lastModified = Instant.parse("2026-09-11T00:00:00Z");
        when(item.objectName()).thenReturn("reports/monthly.pdf");
        when(item.isDir()).thenReturn(false);
        when(item.isDeleteMarker()).thenReturn(false);
        when(client.listObjects(any())).thenReturn(List.of(new Result<>(item)));
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(response);
        when(response.size()).thenReturn(1024L);
        when(response.contentType()).thenReturn("application/pdf");
        when(response.etag()).thenReturn("etag-1");
        when(response.lastModified()).thenReturn(lastModified.atZone(java.time.ZoneOffset.UTC));
        when(response.headers()).thenReturn(okhttp3.Headers.of("Cache-Control", "public, max-age=60"));

        List<ObjectMetadata> objects = adapter(client).list().toList();

        assertEquals(1, objects.size());
        assertEquals("reports/monthly.pdf", objects.getFirst().key().value());
        assertEquals("application/pdf", objects.getFirst().contentType());
        assertEquals(new CacheControl("public, max-age=60"), objects.getFirst().cacheControl());
        verify(client).statObject(any(StatObjectArgs.class));
    }

    private static MinioFileStorageAdapter adapter() {
        return new MinioFileStorageAdapter(new MinioStorageConfiguration(
                "https://minio.example", "uploads", "test-access", "test-secret"));
    }

    private static MinioFileStorageAdapter adapter(MinioClient client) {
        return new MinioFileStorageAdapter(client, "uploads", "https://minio.example");
    }
}
