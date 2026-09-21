package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.CompleteMultipartUploadResult;
import com.obs.services.model.InitiateMultipartUploadResult;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.Multipart;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.TemporarySignatureResponse;
import com.obs.services.model.UploadPartResult;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/** 华为云 OBS 文件存储适配器行为测试。 */
public class HuaweiObsFileStorageAdapterTest {
    private static final String BUCKET = "documents";
    private static final ObjectKey KEY = ObjectKey.of("reports/a.txt");
    private ObsClient client;
    private HuaweiObsFileStorageAdapter storage;

    /** 创建不触发真实网络请求的适配器。 */
    @Before
    public void setUp() {
        client = mock(ObsClient.class);
        storage = new HuaweiObsFileStorageAdapter(client, BUCKET,
                "https://obs.cn-north-4.myhuaweicloud.com");
    }

    /** 上传后应通过 metadata 确认最终对象。 */
    @Test
    public void shouldPutAndConfirmObjectMetadata() {
        when(client.putObject(any())).thenReturn(new PutObjectResult(
                BUCKET, KEY.value(), "etag-1", "https://object", null, null));
        when(client.getObjectMetadata(any())).thenReturn(metadata(5L, "etag-1"));

        io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata result = storage.put(
                new UploadRequest(KEY, new ByteArrayInputStream("hello".getBytes()),
                        5, "text/plain", new CacheControl("public, max-age=60")));

        assertEquals(5L, result.contentLength());
        assertEquals("etag-1", result.etag());
        verify(client).putObject(any());
    }

    /** 预签名上传和下载必须返回对应的 HTTP 方法。 */
    @Test
    public void shouldCreatePutAndGetPresignedRequests() {
        when(client.createTemporarySignature(any())).thenReturn(new TemporarySignatureResponse(
                "https://signed.example/object"));

        var upload = storage.presignUpload(new PresignUploadRequest(
                KEY, Duration.ofMinutes(5), "text/plain", 100));
        var download = storage.presignDownload(new PresignDownloadRequest(
                KEY, Duration.ofMinutes(5), PresignDownloadRequest.ResponseDisposition.INLINE));

        assertEquals("PUT", upload.method());
        assertEquals("GET", download.method());
        assertEquals("https://signed.example/object", upload.url());
        assertEquals("https://signed.example/object", download.url());
    }

    /** 缺失对象应在 stat 和 get 中都转换为空结果。 */
    @Test
    public void shouldReturnEmptyForMissingObject() {
        when(client.getObjectMetadata(any())).thenThrow(obsException(404, "NoSuchKey"));

        assertEquals(Optional.empty(), storage.stat(KEY));
        assertEquals(Optional.empty(), storage.get(KEY));
        verify(client, never()).getObject(any());
    }

    /** 对象列表应惰性请求并映射 OBS 返回的对象元数据。 */
    @Test
    public void shouldListObjectMetadataLazily() {
        ObsObject object = new ObsObject();
        object.setObjectKey(KEY.value());
        object.setMetadata(metadata(5L, "etag-1"));
        when(client.listObjects(any(ListObjectsRequest.class))).thenReturn(new ObjectListing(
                List.of(object), List.of(), BUCKET, false, null, null, 1, null, null, null));

        var listing = storage.list();
        verify(client, never()).listObjects(any(ListObjectsRequest.class));
        assertEquals(KEY, listing.findFirst().orElseThrow().key());
        verify(client).listObjects(any(ListObjectsRequest.class));
    }

    /** multipart 应映射 OBS 的初始化、分片、列举和完成操作。 */
    @Test
    public void shouldCompleteMultipartUploadLifecycle() {
        MultipartUploadRequest request = new MultipartUploadRequest(KEY, 10, 5,
                "text/plain", CacheControl.privateNoCache(), Duration.ofMinutes(10));
        when(client.initiateMultipartUpload(any())).thenReturn(
                new InitiateMultipartUploadResult(BUCKET, KEY.value(), "upload-1"));
        when(client.uploadPart(any())).thenReturn(uploadPartResult(1, "etag-1"));
        when(client.listParts(any())).thenReturn(new com.obs.services.model.ListPartsResult(
                BUCKET, KEY.value(), "upload-1", null, null, null,
                List.of(new Multipart(1, new Date(), "etag-1", 5L)), 1000,
                false, null, null));
        when(client.completeMultipartUpload(any())).thenReturn(new CompleteMultipartUploadResult(
                BUCKET, KEY.value(), "etag-final", "https://object", null, null));
        when(client.getObjectMetadata(any())).thenReturn(metadata(10L, "etag-final"));
        MultipartUploadSession session = storage.initiate(request);
        MultipartPart part = storage.uploadPart(session, new MultipartPartRequest(
                session.uploadId(), 1), new ByteArrayInputStream("hello".getBytes()), 5);

        assertEquals("etag-1", part.etag());
        assertNotNull(storage.listParts(session));
        assertEquals("etag-final", storage.complete(session,
                new MultipartCompletion(List.of(part))).etag());
    }

    /** 创建 OBS metadata 测试对象。 */
    private static ObjectMetadata metadata(long length, String etag) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(length);
        metadata.setContentType("text/plain");
        metadata.setEtag(etag);
        metadata.setLastModified(new Date());
        metadata.setCacheControl("private, max-age=0");
        return metadata;
    }

    /** 创建 OBS 分片上传结果。 */
    private static UploadPartResult uploadPartResult(int number, String etag) {
        UploadPartResult result = new UploadPartResult();
        result.setPartNumber(number);
        result.setEtag(etag);
        return result;
    }

    /** 创建带 HTTP 状态和 OBS 错误码的测试异常。 */
    private static ObsException obsException(int status, String code) {
        ObsException exception = new ObsException("provider detail");
        exception.setResponseCode(status);
        exception.setErrorCode(code);
        return exception;
    }
}
