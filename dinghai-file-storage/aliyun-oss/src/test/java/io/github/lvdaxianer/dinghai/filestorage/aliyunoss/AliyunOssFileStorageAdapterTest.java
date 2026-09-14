package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阿里云 OSS V2 适配器契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public class AliyunOssFileStorageAdapterTest {

    /** 验证 V2 PUT 预签名返回必要请求头且不返回 POST 表单字段。 */
    @Test
    public void createsV2PutPresignWithoutFormFields() {
        OSSClient client = mock(OSSClient.class);
        when(client.presign(any(PutObjectRequest.class), any(PresignOptions.class))).thenReturn(
                PresignResult.newBuilder().url("https://oss.example/upload").method("PUT")
                        .expiration(Instant.EPOCH).signedHeaders(Map.of("Content-Type", "image/png"))
                        .build());

        PresignedUpload result = adapter(client).presignUpload(new PresignUploadRequest(
                ObjectKey.of("users/avatar.png"), Duration.ofMinutes(5), "image/png", 1024,
                new CacheControl("public, max-age=60")));

        assertEquals("PUT", result.method());
        assertEquals("image/png", result.requiredHeaders().get("Content-Type"));
        assertTrue(result.formFields().isEmpty());
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).presign(request.capture(), any(PresignOptions.class));
        assertEquals("uploads", request.getValue().bucket());
        assertEquals("users/avatar.png", request.getValue().key());
        assertEquals("public, max-age=60", request.getValue().cacheControl());
    }

    /** 验证 V2 GET 预签名绑定响应缓存与展示模式。 */
    @Test
    public void createsV2GetPresignWithResponseOverrides() {
        OSSClient client = mock(OSSClient.class);
        when(client.presign(any(GetObjectRequest.class), any(PresignOptions.class))).thenReturn(
                PresignResult.newBuilder().url("https://oss.example/download").method("GET")
                        .expiration(Instant.EPOCH).build());

        PresignedDownload result = adapter(client).presignDownload(new PresignDownloadRequest(
                ObjectKey.of("reports/a.pdf"), Duration.ofMinutes(5),
                PresignDownloadRequest.ResponseDisposition.INLINE,
                new CacheControl("private, max-age=30")));

        assertEquals("GET", result.method());
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).presign(request.capture(), any(PresignOptions.class));
        assertEquals("private, max-age=30", request.getValue().responseCacheControl());
        assertEquals("inline", request.getValue().responseContentDisposition());
    }

    /** 验证写入请求固定到配置 bucket，并使用 V2 流式正文。 */
    @Test
    public void writesToFixedBucketWithV2Request() {
        OSSClient client = mock(OSSClient.class);
        ObjectKey key = ObjectKey.of("reports/monthly.pdf");
        when(client.headObject(any())).thenReturn(metadata());

        adapter(client).put(new UploadRequest(key, new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L, "application/pdf", new CacheControl("public, max-age=60")));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture());
        assertEquals("uploads", request.getValue().bucket());
        assertEquals(key.value(), request.getValue().key());
        assertEquals(3L, request.getValue().body().getLength().longValue());
    }

    /** 验证服务端对象缺失映射为统一空结果。 */
    @Test
    public void returnsEmptyForMissingObject() {
        OSSClient client = mock(OSSClient.class);
        when(client.headObject(any())).thenThrow(serviceException("NoSuchKey"));

        assertTrue(adapter(client).stat(ObjectKey.of("missing.txt")).isEmpty());
    }

    /** 验证认证失败不泄露 SDK 原始响应。 */
    @Test
    public void mapsAuthenticationFailureWithoutLeakingDetails() {
        OSSClient client = mock(OSSClient.class);
        when(client.headObject(any())).thenThrow(serviceException("AccessDenied"));

        try {
            adapter(client).stat(ObjectKey.of("private.txt"));
            fail("expected mapped storage failure");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.AUTHENTICATION_FAILED, expected.errorCode());
            assertEquals("OSS operation failed", expected.getMessage());
        }
    }

    /** 验证缺失部署环境凭证时在任何远端调用前失败。 */
    @Test
    public void rejectsMissingEnvironmentCredentialsBeforeConnecting() {
        Assume.assumeTrue(environmentMissing(AliyunOssClientFactory.ACCESS_KEY_ID_ENVIRONMENT)
                || environmentMissing(AliyunOssClientFactory.ACCESS_KEY_SECRET_ENVIRONMENT));

        try {
            AliyunOssClientFactory.create(new AliyunOssStorageConfiguration("oss-cn-beijing", "uploads"));
            fail("expected missing environment credential failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("OSS_ACCESS_KEY"));
        }
    }

    /** 创建固定 bucket 的 V2 适配器。 */
    private static AliyunOssFileStorageAdapter adapter(OSSClient client) {
        return new AliyunOssFileStorageAdapter(client, "uploads");
    }

    /** 创建完整的 V2 HEAD 响应。 */
    private static HeadObjectResult metadata() {
        return HeadObjectResult.newBuilder().headers(Map.of("Content-Type", "application/pdf",
                "Content-Length", "3", "ETag", "etag-1", "Last-Modified",
                "Thu, 01 Jan 1970 00:00:00 GMT", "Cache-Control", "public, max-age=60")).build();
    }

    /** 创建含稳定 OSS 错误码的 V2 服务端异常。 */
    private static ServiceException serviceException(String code) {
        return ServiceException.newBuilder().statusCode(404)
                .errorFields(Map.of("Code", code, "Message", "sensitive response")).build();
    }

    /** 判断部署环境变量是否缺失。 */
    private static boolean environmentMissing(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank();
    }
}
