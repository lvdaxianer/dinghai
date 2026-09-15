package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 用户提供 RustFS 服务的 S3 对象生命周期验收测试。
 *
 * 默认跳过，仅在显式提供临时测试凭据时执行；测试始终创建独立私有 bucket 并在 finally 中删除。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
public class RustfsLifecycleIntegrationTest {

    /** 启用 RustFS 验收测试的环境变量。 */
    private static final String ENABLED = "DINGHAI_FILE_STORAGE_RUSTFS_IT";
    /** RustFS S3 API 端点环境变量。 */
    private static final String ENDPOINT = "DINGHAI_FILE_STORAGE_RUSTFS_ENDPOINT";
    /** RustFS 访问密钥环境变量。 */
    private static final String ACCESS_KEY = "DINGHAI_FILE_STORAGE_RUSTFS_ACCESS_KEY";
    /** RustFS 私密访问密钥环境变量。 */
    private static final String SECRET_KEY = "DINGHAI_FILE_STORAGE_RUSTFS_SECRET_KEY";
    /** 生命周期测试对象 key。 */
    private static final ObjectKey LIFECYCLE_KEY = ObjectKey.of("lifecycle/private.txt");
    /** 预签名上传对象 key。 */
    private static final ObjectKey PRESIGNED_KEY = ObjectKey.of("presigned/upload.txt");
    /** multipart 生命周期测试对象 key。 */
    private static final ObjectKey MULTIPART_KEY = ObjectKey.of("multipart/recovered.bin");
    /** 测试对象内容类型。 */
    private static final String CONTENT_TYPE = "text/plain";
    /** multipart 文件字段名称。 */
    private static final String FILE_FIELD_NAME = "file";
    /** multipart 上传文件名称。 */
    private static final String FILE_NAME = "upload.txt";
    /** HTTP 成功状态码下界。 */
    private static final int HTTP_SUCCESS_MIN = 200;
    /** HTTP 成功状态码上界（不包含）。 */
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;
    /** HTTP 建连超时。 */
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 预签名有效期。 */
    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(5);
    /** S3 非末分片最小大小。 */
    private static final int PART_SIZE = 5 * 1024 * 1024;

    /**
     * RustFS 私有 bucket 必须支持统一对象生命周期和原生预签名表单。
     *
     * @throws Exception 远端对象操作或 HTTP 预签名请求失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Test
    public void shouldCompleteLifecycleAndNativePresigningAgainstRustfs() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        String bucket = temporaryBucket();
        MinioClient client = client();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try {
            verifyObjectLifecycle(storage(bucket));
            verifyNativePresigning(storage(bucket));
            verifyMultipartRecovery(storage(bucket), bucket);
        } finally {
            removeTemporaryBucket(client, bucket);
        }
    }

    /**
     * 验证写入、查询、读取、列举和删除使用相同的私有 bucket。
     *
     * @param storage RustFS 文件存储适配器
     * @throws Exception 读取远端对象失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void verifyObjectLifecycle(RustfsFileStorageAdapter storage) throws Exception {
        byte[] content = "rustfs-lifecycle".getBytes(StandardCharsets.UTF_8);
        storage.put(new UploadRequest(LIFECYCLE_KEY, new ByteArrayInputStream(content), content.length,
                CONTENT_TYPE, CacheControl.privateNoCache()));
        assertTrue(storage.exists(LIFECYCLE_KEY));
        assertEquals(content.length, storage.stat(LIFECYCLE_KEY).orElseThrow().contentLength());
        try (var object = storage.get(LIFECYCLE_KEY).orElseThrow()) {
            assertArrayEquals(content, object.stream().readAllBytes());
        }
        try (var objects = storage.list()) {
            assertTrue(objects.anyMatch(metadata -> metadata.key().equals(LIFECYCLE_KEY)));
        }
        assertTrue(storage.delete(LIFECYCLE_KEY));
    }

    /**
     * 验证 RustFS 原生 POST 上传表单和 GET 下载地址都可实际访问。
     *
     * @param storage RustFS 文件存储适配器
     * @throws Exception 预签名 HTTP 请求失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void verifyNativePresigning(RustfsFileStorageAdapter storage) throws Exception {
        byte[] content = "rustfs-presigned-content".getBytes(StandardCharsets.UTF_8);
        PresignedUpload upload = storage.presignUpload(new PresignUploadRequest(PRESIGNED_KEY,
                PRESIGN_EXPIRY, CONTENT_TYPE, content.length, CacheControl.privateNoCache()));
        HttpResponse<String> uploadResponse = httpClient().send(presignedPost(upload, content),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(uploadResponse.statusCode() >= HTTP_SUCCESS_MIN
                && uploadResponse.statusCode() < HTTP_SUCCESS_MAX_EXCLUSIVE);
        String downloadUrl = storage.presignDownload(new PresignDownloadRequest(PRESIGNED_KEY,
                PRESIGN_EXPIRY, PresignDownloadRequest.ResponseDisposition.INLINE,
                CacheControl.privateNoCache())).uri();
        HttpResponse<byte[]> downloadResponse = httpClient().send(HttpRequest.newBuilder(
                URI.create(downloadUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(HTTP_SUCCESS_MIN, downloadResponse.statusCode());
        assertArrayEquals(content, downloadResponse.body());
    }

    /** 验证 RustFS multipart 支持重试、适配器重建后的恢复和完成。 */
    private void verifyMultipartRecovery(RustfsFileStorageAdapter first, String bucket) throws Exception {
        MultipartUploadSession session = first.initiate(new MultipartUploadRequest(MULTIPART_KEY,
                PART_SIZE * 2L, PART_SIZE, "application/octet-stream", CacheControl.privateNoCache(),
                PRESIGN_EXPIRY));
        try {
            uploadPresignedPart(first, session, 1, (byte) 'a');
            MultipartPart initial = first.listParts(session).getFirst();
            uploadPresignedPart(first, session, 1, (byte) 'b');
            MultipartPart retried = first.listParts(session).getFirst();
            uploadPresignedPart(first, session, 2, (byte) 'c');
            MultipartPart second = first.listParts(session).get(1);

            RustfsFileStorageAdapter recovered = storage(bucket);
            assertEquals(java.util.List.of(retried, second), recovered.listParts(session));
            assertEquals(PART_SIZE * 2L, recovered.complete(session,
                    new MultipartCompletion(java.util.List.of(retried, second))).contentLength());
            try (var object = recovered.get(MULTIPART_KEY).orElseThrow()) {
                assertEquals('b', object.stream().read());
            }
            assertNotEquals(initial.etag(), retried.etag());
        } finally {
            first.abort(session);
        }
    }

    /** 创建绑定会话和总分片数的请求。 */
    private MultipartPartRequest partRequest(MultipartUploadSession session, int partNumber) {
        return new MultipartPartRequest(session.uploadId(), partNumber, session.partCount());
    }

    /** 使用 RustFS 签发的原生 URL 直接上传一个分片。 */
    private void uploadPresignedPart(RustfsFileStorageAdapter storage, MultipartUploadSession session,
                                     int partNumber, byte value) throws Exception {
        PresignedUpload upload = storage.presignPart(session, partRequest(session, partNumber));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.uri()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content(value)));
        upload.requiredHeaders().forEach(request::header);
        HttpResponse<Void> response = httpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
        assertTrue(response.statusCode() >= HTTP_SUCCESS_MIN && response.statusCode() < HTTP_SUCCESS_MAX_EXCLUSIVE);
    }

    /** 创建指定长度的固定分片内容。 */
    private byte[] content(byte value) {
        byte[] content = new byte[PART_SIZE];
        java.util.Arrays.fill(content, value);
        return content;
    }

    /**
     * 按 RustFS 签发的表单字段构建 multipart POST 请求。
     *
     * @param upload RustFS 签发的上传表单
     * @param content 待上传对象内容
     * @return 可直接发送的 HTTP 请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private HttpRequest presignedPost(PresignedUpload upload, byte[] content) {
        String boundary = "dinghai-" + UUID.randomUUID();
        return HttpRequest.newBuilder(URI.create(upload.uri()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(upload, content, boundary)))
                .build();
    }

    /**
     * 编码 RustFS POST policy 要求的表单字段及文件段。
     *
     * @param upload RustFS 签发的上传表单
     * @param content 待上传对象内容
     * @param boundary multipart 分隔符
     * @return multipart 请求体
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private byte[] multipartBody(PresignedUpload upload, byte[] content, String boundary) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        upload.formFields().forEach((name, value) -> writeField(body, boundary, name, value));
        writeFile(body, boundary, content);
        write(body, "--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    /**
     * 写入一个 multipart 文本字段。
     *
     * @param body multipart 请求体
     * @param boundary multipart 分隔符
     * @param name 字段名称
     * @param value 字段值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void writeField(ByteArrayOutputStream body, String boundary, String name, String value) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(body, value + "\r\n");
    }

    /**
     * 写入 multipart 文件字段。
     *
     * @param body multipart 请求体
     * @param boundary multipart 分隔符
     * @param content 待上传对象内容
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void writeFile(ByteArrayOutputStream body, String boundary, byte[] content) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"" + FILE_FIELD_NAME
                + "\"; filename=\"" + FILE_NAME + "\"\r\n");
        write(body, "Content-Type: " + CONTENT_TYPE + "\r\n\r\n");
        body.writeBytes(content);
        write(body, "\r\n");
    }

    /**
     * 写入 UTF-8 multipart 片段。
     *
     * @param body multipart 请求体
     * @param value 文本片段
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void write(ByteArrayOutputStream body, String value) {
        body.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建用于签桶和清理的 RustFS S3 客户端。
     *
     * @return 已配置客户端
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private MinioClient client() {
        return MinioClient.builder().endpoint(required(ENDPOINT)).credentials(required(ACCESS_KEY),
                required(SECRET_KEY)).build();
    }

    /**
     * 创建固定临时 bucket 的 RustFS 适配器。
     *
     * @param bucket 已创建的测试 bucket
     * @return RustFS 文件存储适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private RustfsFileStorageAdapter storage(String bucket) {
        return new RustfsFileStorageAdapter(new RustfsStorageConfiguration(required(ENDPOINT), bucket,
                required(ACCESS_KEY), required(SECRET_KEY)));
    }

    /**
     * 创建短小且符合 S3 bucket 规则的临时名称。
     *
     * @return 临时 bucket 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private String temporaryBucket() {
        return "dhrustfs" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 删除测试对象和临时 bucket，避免远端残留。
     *
     * @param client RustFS S3 客户端
     * @param bucket 临时 bucket 名称
     * @throws Exception 删除远端测试资源失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void removeTemporaryBucket(MinioClient client, String bucket) throws Exception {
        removeObject(client, bucket, LIFECYCLE_KEY);
        removeObject(client, bucket, PRESIGNED_KEY);
        removeObject(client, bucket, MULTIPART_KEY);
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
    }

    /**
     * 尽力删除指定测试对象，缺失对象由服务端按幂等删除处理。
     *
     * @param client RustFS S3 客户端
     * @param bucket 临时 bucket 名称
     * @param key 待删除对象 key
     * @throws Exception 删除远端对象失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private void removeObject(MinioClient client, String bucket, ObjectKey key) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build());
    }

    /**
     * 创建不保存 Cookie 或鉴权状态的 HTTP 客户端。
     *
     * @return HTTP 客户端
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
    }

    /**
     * 读取必需测试配置但不输出其值。
     *
     * @param name 环境变量名
     * @return 非空环境变量值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private String required(String name) {
        String value = System.getenv(name);
        Assume.assumeTrue(value != null && !value.isBlank());
        return value;
    }
}
