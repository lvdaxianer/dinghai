package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.CompleteMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.AbortMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.InitiateMultipartUploadRequest;
import com.aliyun.sdk.service.oss2.models.InitiateMultipartUploadResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListPartsRequest;
import com.aliyun.sdk.service.oss2.models.ListPartsResult;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.UploadPartRequest;
import com.aliyun.sdk.service.oss2.models.UploadPartResult;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
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
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

    /** 阿里云 OSS 控制台地域 ID。 */
    private static final String CONSOLE_REGION = "oss-cn-beijing";
    /** OSS V2 SDK 地域 ID。 */
    private static final String SDK_REGION = "cn-beijing";

    /** 验证控制台地域 ID 转换为 OSS V2 SDK 所需格式。 */
    @Test
    public void normalizesAliyunConsoleRegionForOssV2Sdk() {
        assertEquals(SDK_REGION, AliyunOssClientFactory.sdkRegion(CONSOLE_REGION));
    }

    /** 验证已是 SDK 格式的地域 ID 保持不变。 */
    @Test
    public void preservesOssV2SdkRegion() {
        assertEquals(SDK_REGION, AliyunOssClientFactory.sdkRegion(SDK_REGION));
    }

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

    /**
     * 验证首个列表请求不向 OSS V2 builder 传递空 continuation token。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void omitsContinuationTokenFromFirstV2ListRequest() {
        OSSClient client = mock(OSSClient.class);
        IllegalStateException expected = new IllegalStateException("captured list request");
        when(client.listObjectsV2(any())).thenThrow(expected);

        try (var objects = adapter(client).list()) {
            objects.findFirst();
            fail("expected captured list request");
        } catch (IllegalStateException actual) {
            assertEquals(expected, actual);
        }

        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(client).listObjectsV2(request.capture());
        assertEquals("uploads", request.getValue().bucket());
        assertNull(request.getValue().continuationToken());
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

    /** OSS multipart 初始化必须固定 bucket、对象元数据并返回原生 uploadId。 */
    @Test
    public void initiatesNativeMultipartUpload() {
        OSSClient client = mock(OSSClient.class);
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(
                InitiateMultipartUploadResult.newBuilder().innerBody(
                        com.aliyun.sdk.service.oss2.models.InitiateMultipartUpload.newBuilder()
                                .uploadId("upload-id").build()).build());
        MultipartUploadRequest request = multipartRequest();

        MultipartUploadSession session = adapter(client).initiate(request);

        assertEquals("upload-id", session.uploadId());
        ArgumentCaptor<InitiateMultipartUploadRequest> captured = ArgumentCaptor.forClass(
                InitiateMultipartUploadRequest.class);
        verify(client).initiateMultipartUpload(captured.capture());
        assertEquals("uploads", captured.getValue().bucket());
        assertEquals("large.bin", captured.getValue().key());
        assertEquals("application/octet-stream", captured.getValue().contentType());
        assertEquals("private", captured.getValue().cacheControl());
    }

    /** OSS 分片预签名必须绑定会话、对象和分片号。 */
    @Test
    public void presignsNativeUploadPart() {
        OSSClient client = mock(OSSClient.class);
        when(client.presign(any(UploadPartRequest.class), any(PresignOptions.class))).thenReturn(
                PresignResult.newBuilder().url("https://oss.example/part").method("PUT")
                        .expiration(Instant.EPOCH).build());
        MultipartUploadSession session = multipartSession();

        PresignedUpload result = adapter(client).presignPart(session,
                new MultipartPartRequest(session.uploadId(), 2, session.partCount()));

        assertEquals("PUT", result.method());
        ArgumentCaptor<UploadPartRequest> captured = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client).presign(captured.capture(), any(PresignOptions.class));
        assertEquals("uploads", captured.getValue().bucket());
        assertEquals("large.bin", captured.getValue().key());
        assertEquals("upload-id", captured.getValue().uploadId());
        assertEquals(Long.valueOf(2L), captured.getValue().partNumber());
        assertTrue(adapter(client) instanceof MultipartFileStorage);
    }

    /** OSS 原生分片上传与断点查询必须保留远端确认的 ETag 和长度。 */
    @Test
    public void uploadsAndListsNativeMultipartParts() {
        OSSClient client = mock(OSSClient.class);
        when(client.uploadPart(any(UploadPartRequest.class))).thenReturn(UploadPartResult.newBuilder()
                .headers(Map.of("ETag", "etag-2")).build());
        com.aliyun.sdk.service.oss2.models.internal.ListPartResultXml listBody =
                new com.aliyun.sdk.service.oss2.models.internal.ListPartResultXml();
        listBody.parts = List.of(com.aliyun.sdk.service.oss2.models.Part.newBuilder().partNumber(2L)
                .size(3L).eTag("etag-2").build());
        when(client.listParts(any(ListPartsRequest.class))).thenReturn(ListPartsResult.newBuilder()
                .innerBody(listBody).build());
        MultipartUploadSession session = multipartSession();
        MultipartPartRequest request = new MultipartPartRequest(session.uploadId(), 2, session.partCount());

        var uploaded = adapter(client).uploadPart(session, request,
                new ByteArrayInputStream(new byte[] {4, 5, 6}), 3L);
        var parts = adapter(client).listParts(session);

        assertEquals("etag-2", uploaded.etag());
        assertEquals(3L, uploaded.size());
        assertEquals(List.of(uploaded), parts);
        ArgumentCaptor<UploadPartRequest> uploadedRequest = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client).uploadPart(uploadedRequest.capture());
        assertEquals("upload-id", uploadedRequest.getValue().uploadId());
        assertEquals(Long.valueOf(2L), uploadedRequest.getValue().partNumber());
        verify(client).listParts(any(ListPartsRequest.class));
    }

    /** OSS 完成与取消请求必须绑定同一会话、对象和 ETag 清单。 */
    @Test
    public void completesAndAbortsNativeMultipartUpload() {
        OSSClient client = mock(OSSClient.class);
        MultipartUploadSession session = multipartSession();
        MultipartCompletion completion = new MultipartCompletion(List.of(
                new MultipartPart(1, 3L, "etag-1"), new MultipartPart(2, 3L, "etag-2")));
        when(client.headObject(any())).thenReturn(metadata());

        adapter(client).complete(session, completion);
        adapter(client).abort(session);

        ArgumentCaptor<CompleteMultipartUploadRequest> complete = ArgumentCaptor.forClass(
                CompleteMultipartUploadRequest.class);
        verify(client).completeMultipartUpload(complete.capture());
        assertEquals("uploads", complete.getValue().bucket());
        assertEquals("large.bin", complete.getValue().key());
        assertEquals("upload-id", complete.getValue().uploadId());
        assertEquals("etag-1", complete.getValue().completeMultipartUpload().parts().getFirst().eTag());
        ArgumentCaptor<AbortMultipartUploadRequest> abort = ArgumentCaptor.forClass(
                AbortMultipartUploadRequest.class);
        verify(client).abortMultipartUpload(abort.capture());
        assertEquals("uploads", abort.getValue().bucket());
        assertEquals("large.bin", abort.getValue().key());
        assertEquals("upload-id", abort.getValue().uploadId());
    }

    /** OSS 返回 NoSuchUpload 时取消必须仍保持幂等。 */
    @Test
    public void treatsMissingNativeMultipartUploadAsAlreadyAborted() {
        OSSClient client = mock(OSSClient.class);
        when(client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenThrow(serviceException("NoSuchUpload"));

        adapter(client).abort(multipartSession());

        verify(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /** 创建固定 bucket 的 V2 适配器。 */
    private static AliyunOssFileStorageAdapter adapter(OSSClient client) {
        return new AliyunOssFileStorageAdapter(client, "uploads");
    }

    /** 创建测试 multipart 请求。 */
    private static MultipartUploadRequest multipartRequest() {
        return new MultipartUploadRequest(ObjectKey.of("large.bin"), 6L, 3L,
                "application/octet-stream", new CacheControl("private"), Duration.ofMinutes(5));
    }

    /** 创建测试 multipart 会话。 */
    private static MultipartUploadSession multipartSession() {
        return new MultipartUploadSession("upload-id", multipartRequest(), Instant.now().plusSeconds(300));
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
