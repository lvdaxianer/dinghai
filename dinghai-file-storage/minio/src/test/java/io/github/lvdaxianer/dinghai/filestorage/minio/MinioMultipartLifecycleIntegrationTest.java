package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 用户提供 MinIO 服务的 multipart 生命周期验收测试。
 *
 * 默认跳过，仅在显式提供临时测试凭据时执行；测试使用独立 bucket 并在 finally 中删除。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public class MinioMultipartLifecycleIntegrationTest {

    /** 启用 MinIO 验收测试的环境变量。 */
    private static final String ENABLED = "DINGHAI_FILE_STORAGE_MINIO_IT";
    /** MinIO S3 API 端点环境变量。 */
    private static final String ENDPOINT = "DINGHAI_FILE_STORAGE_MINIO_ENDPOINT";
    /** MinIO 访问密钥环境变量。 */
    private static final String ACCESS_KEY = "DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY";
    /** MinIO 私密访问密钥环境变量。 */
    private static final String SECRET_KEY = "DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY";
    /** S3 非末分片最小大小。 */
    private static final int PART_SIZE = 5 * 1024 * 1024;
    /** 本次测试完成对象的固定路径。 */
    private static final ObjectKey COMPLETED_KEY = ObjectKey.of("multipart/recovered.bin");

    /** MinIO multipart 必须支持分片重试、适配器重建后的恢复和完成。 */
    @Test
    public void shouldRetryRecoverAndCompleteMultipartUploadAgainstMinio() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        MinioClient client = client();
        String bucket = temporaryBucket();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        MultipartUploadSession session = null;
        try {
            MinioFileStorageAdapter first = storage(bucket);
            session = first.initiate(request(COMPLETED_KEY));
            uploadPresignedPart(first, session, 1, (byte) 'a');
            MultipartPart initial = first.listParts(session).getFirst();
            uploadPresignedPart(first, session, 1, (byte) 'b');
            MultipartPart retried = first.listParts(session).getFirst();
            uploadPresignedPart(first, session, 2, (byte) 'c');
            MultipartPart second = first.listParts(session).get(1);

            MinioFileStorageAdapter recovered = storage(bucket);
            assertEquals(List.of(retried, second), recovered.listParts(session));
            assertEquals(PART_SIZE * 2L, recovered.complete(session,
                    new MultipartCompletion(List.of(retried, second))).contentLength());
            try (var object = recovered.get(COMPLETED_KEY).orElseThrow()) {
                assertEquals('b', object.stream().read());
            }
            org.junit.Assert.assertNotEquals(initial.etag(), retried.etag());
        } finally {
            if (session != null) {
                storage(bucket).abort(session);
            }
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(COMPLETED_KEY.value()).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }

    /** 创建符合 provider 最小分片约束的两片上传请求。 */
    private MultipartUploadRequest request(ObjectKey key) {
        return new MultipartUploadRequest(key, PART_SIZE * 2L, PART_SIZE, "application/octet-stream",
                CacheControl.privateNoCache(), Duration.ofMinutes(10L));
    }

    /** 创建绑定会话和总片数的分片请求。 */
    private MultipartPartRequest partRequest(MultipartUploadSession session, int partNumber) {
        return new MultipartPartRequest(session.uploadId(), partNumber, session.partCount());
    }

    /** 使用 provider 签发的原生 URL 直接上传一个分片。 */
    private void uploadPresignedPart(MinioFileStorageAdapter storage, MultipartUploadSession session,
                                     int partNumber, byte value) throws Exception {
        var upload = storage.presignPart(session, partRequest(session, partNumber));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.uri()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content(value)));
        upload.requiredHeaders().forEach(request::header);
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request.build(),
                HttpResponse.BodyHandlers.discarding());
        org.junit.Assert.assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
    }

    /** 创建固定字节的指定长度内容。 */
    private byte[] content(byte value) {
        byte[] bytes = new byte[PART_SIZE];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    /** 创建连接用户提供 MinIO 的客户端。 */
    private MinioClient client() {
        return MinioClient.builder().endpoint(required(ENDPOINT)).credentials(required(ACCESS_KEY),
                required(SECRET_KEY)).build();
    }

    /** 创建固定临时 bucket 的 MinIO 适配器。 */
    private MinioFileStorageAdapter storage(String bucket) {
        return new MinioFileStorageAdapter(client(), bucket, required(ENDPOINT));
    }

    /** 创建符合 S3 bucket 规则的随机测试名称。 */
    private String temporaryBucket() {
        return "dhmultipart" + UUID.randomUUID().toString().replace("-", "");
    }

    /** 读取必需测试配置但不输出其值。 */
    private String required(String name) {
        String value = System.getenv(name);
        Assume.assumeTrue(value != null && !value.isBlank());
        return value;
    }
}
