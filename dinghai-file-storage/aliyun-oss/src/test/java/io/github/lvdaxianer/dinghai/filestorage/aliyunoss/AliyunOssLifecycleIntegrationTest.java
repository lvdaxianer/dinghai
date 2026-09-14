package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 使用用户提供的阿里云 OSS bucket 进行的生命周期验收测试。
 *
 * 默认跳过，仅当部署环境提供 OSS AccessKey 时执行；测试只创建随机前缀对象并在 finally 中清理。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public class AliyunOssLifecycleIntegrationTest {

    /** 阿里云 OSS 测试 bucket。 */
    private static final String BUCKET = "dinghai";
    /** 阿里云 OSS 测试地域。 */
    private static final String REGION = "oss-cn-beijing";
    /** 测试对象内容类型。 */
    private static final String CONTENT_TYPE = "text/plain";
    /** 测试对象缓存策略。 */
    private static final CacheControl CACHE_CONTROL = CacheControl.privateNoCache();
    /** 预签名有效期。 */
    private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(5);
    /** HTTP 建连超时。 */
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** HTTP 成功状态码。 */
    private static final int HTTP_OK = 200;
    /** HTTP 成功状态码下界。 */
    private static final int HTTP_SUCCESS_MIN = 200;
    /** HTTP 成功状态码上界（不包含）。 */
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;

    /**
     * 固定 bucket 必须支持完整对象生命周期和原生 PUT/GET 预签名。
     *
     * @throws Exception 远端对象操作或 HTTP 请求失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    @Test
    public void shouldCompleteLifecycleAndNativePresigningAgainstAliyunOss() throws Exception {
        assumeCredentialsPresent();
        AliyunOssFileStorageAdapter storage = storage();
        ObjectKey lifecycleKey = randomKey("lifecycle");
        ObjectKey presignedKey = randomKey("presigned");
        Exception failure = null;
        try {
            verifyObjectLifecycle(storage, lifecycleKey);
            verifyNativePresigning(storage, presignedKey);
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            deleteAll(storage, failure, lifecycleKey, presignedKey);
        }
    }

    /**
     * 验证对象写入、查询、读取、列举和删除。
     *
     * @param storage 阿里云 OSS 适配器
     * @param key 本次测试随机对象 key
     * @throws Exception 读取远端对象失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private void verifyObjectLifecycle(AliyunOssFileStorageAdapter storage, ObjectKey key) throws Exception {
        byte[] content = "aliyun-oss-lifecycle".getBytes(StandardCharsets.UTF_8);
        storage.put(new UploadRequest(key, new ByteArrayInputStream(content), content.length,
                CONTENT_TYPE, CACHE_CONTROL));
        assertTrue(storage.exists(key));
        assertEquals(content.length, storage.stat(key).orElseThrow().contentLength());
        try (var object = storage.get(key).orElseThrow()) {
            assertArrayEquals(content, object.stream().readAllBytes());
        }
        try (var objects = storage.list()) {
            assertTrue(objects.anyMatch(metadata -> metadata.key().equals(key)));
        }
        assertTrue(storage.delete(key));
        assertTrue(storage.stat(key).isEmpty());
    }

    /**
     * 验证 OSS V2 原生 PUT 上传和 GET 下载预签名均可实际访问。
     *
     * @param storage 阿里云 OSS 适配器
     * @param key 本次测试随机对象 key
     * @throws Exception 预签名 HTTP 请求失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private void verifyNativePresigning(AliyunOssFileStorageAdapter storage, ObjectKey key) throws Exception {
        byte[] content = "aliyun-oss-presigned-content".getBytes(StandardCharsets.UTF_8);
        PresignedUpload upload = storage.presignUpload(new PresignUploadRequest(key, PRESIGN_EXPIRY,
                CONTENT_TYPE, content.length, CACHE_CONTROL));
        HttpResponse<String> uploadResponse = httpClient().send(presignedPut(upload, content),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(uploadResponse.statusCode() >= HTTP_SUCCESS_MIN
                && uploadResponse.statusCode() < HTTP_SUCCESS_MAX_EXCLUSIVE);
        String downloadUrl = storage.presignDownload(new PresignDownloadRequest(key, PRESIGN_EXPIRY,
                PresignDownloadRequest.ResponseDisposition.INLINE, CACHE_CONTROL)).uri();
        HttpResponse<byte[]> downloadResponse = httpClient().send(HttpRequest.newBuilder(URI.create(downloadUrl))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(HTTP_OK, downloadResponse.statusCode());
        assertArrayEquals(content, downloadResponse.body());
    }

    /**
     * 构建带 V2 签名必需请求头的 PUT 请求。
     *
     * @param upload OSS V2 签发的上传信息
     * @param content 待上传内容
     * @return 可直接发送的 PUT 请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private HttpRequest presignedPut(PresignedUpload upload, byte[] content) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(upload.uri()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        upload.requiredHeaders().forEach(builder::header);
        return builder.build();
    }

    /**
     * 创建仅使用显式连接超时的 HTTP 客户端。
     *
     * @return HTTP 客户端
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
    }

    /**
     * 创建固定用户 bucket 的 OSS 适配器。
     *
     * @return 使用部署环境凭证的阿里云 OSS 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private AliyunOssFileStorageAdapter storage() {
        return new AliyunOssFileStorageAdapter(new AliyunOssStorageConfiguration(REGION, BUCKET));
    }

    /**
     * 创建隔离于业务对象的随机测试 key。
     *
     * @param operation 测试操作名称
     * @return 仅供本次验收使用的对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private ObjectKey randomKey(String operation) {
        return ObjectKey.of("__dinghai_it__/" + operation + "/" + UUID.randomUUID() + ".txt");
    }

    /**
     * 删除测试对象，避免验收失败时在用户 bucket 中遗留数据。
     *
     * @param storage 阿里云 OSS 适配器
     * @param key 待清理对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private void deleteAll(AliyunOssFileStorageAdapter storage, Exception failure, ObjectKey... keys) {
        RuntimeException cleanupFailure = null;
        for (ObjectKey key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                cleanupFailure = append(cleanupFailure, exception);
            }
        }
        if (cleanupFailure != null && failure != null) {
            failure.addSuppressed(cleanupFailure);
        } else if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    /**
     * 聚合清理失败，以便继续清理后续随机对象。
     *
     * @param failure 已捕获的清理失败
     * @param exception 本次清理失败
     * @return 聚合后的清理失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private RuntimeException append(RuntimeException failure, RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    /**
     * 在缺少部署凭证时跳过真实云服务测试。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private void assumeCredentialsPresent() {
        Assume.assumeTrue(hasEnvironment(AliyunOssClientFactory.ACCESS_KEY_ID_ENVIRONMENT)
                && hasEnvironment(AliyunOssClientFactory.ACCESS_KEY_SECRET_ENVIRONMENT));
    }

    /**
     * 判断指定环境变量是否包含非空值。
     *
     * @param name 环境变量名称
     * @return 环境变量是否可用
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private boolean hasEnvironment(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
