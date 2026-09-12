package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** LocalFileStorageSigner 的签名边界测试。 */
public class LocalFileStorageSignerTest {
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private static final byte[] KEY = "local-signing-secret-that-is-long".getBytes(StandardCharsets.UTF_8);

    /** 上传签名绑定 key、类型、大小和缓存策略，并可通过验签。 */
    @Test
    public void signsAndVerifiesUploadPolicy() {
        LocalFileStorageSigner signer = signer();
        PresignUploadRequest request = new PresignUploadRequest(ObjectKey.of("1/1/1/1.png"),
                Duration.ofMinutes(10), "image/png", 4096,
                new CacheControl("public, max-age=60"));

        PresignedUpload result = signer.presignUpload(request, "/files");

        assertEquals("PUT", result.method());
        assertTrue(result.url().contains("/files/upload/1/1/1/1.png"));
        signer.verifyUpload(request, result.expiresAt(), queryParameter(result.url(), "signature"), NOW);
    }

    /** 下载签名绑定 inline 模式和缓存策略，并可通过验签。 */
    @Test
    public void signsAndVerifiesDownloadPolicy() {
        LocalFileStorageSigner signer = signer();
        PresignDownloadRequest request = new PresignDownloadRequest(ObjectKey.of("用户资料/头像.png"),
                Duration.ofMinutes(10), PresignDownloadRequest.ResponseDisposition.INLINE,
                new CacheControl("private, max-age=30"));

        PresignedDownload result = signer.presignDownload(request, "/files");

        assertEquals("GET", result.method());
        assertTrue(result.url().contains("disposition=inline"));
        signer.verifyDownload(request, result.expiresAt(), queryParameter(result.url(), "signature"), NOW);
    }

    /** 篡改签名、方法或策略必须失败。 */
    @Test
    public void rejectsTamperedSignatureAndPolicy() {
        LocalFileStorageSigner signer = signer();
        PresignUploadRequest request = new PresignUploadRequest(ObjectKey.of("file.txt"),
                Duration.ofMinutes(10), "text/plain", 20);
        PresignedUpload result = signer.presignUpload(request, "/files");

        expectInvalid(() -> signer.verifyUpload(
                new PresignUploadRequest(ObjectKey.of("other.txt"), Duration.ofMinutes(10),
                "text/plain", 20), result.expiresAt(), queryParameter(result.url(), "signature"), NOW));
        expectInvalid(() -> signer.verifyUpload(request, result.expiresAt(), "tampered", NOW));
        expectInvalid(() -> signer.verifyUpload(request, NOW.minusSeconds(1),
                queryParameter(result.url(), "signature"), NOW));
        expectInvalid(() -> signer.verifyUpload("POST", request, result.expiresAt(),
                queryParameter(result.url(), "signature"), NOW));
        expectInvalid(() -> signer.verifyUpload(new PresignUploadRequest(ObjectKey.of("file.txt"),
                Duration.ofMinutes(10), "application/json", 20), result.expiresAt(),
                queryParameter(result.url(), "signature"), NOW));
    }

    /** 签名有效期不能超过本地适配器上限。 */
    @Test
    public void rejectsExpiryBeyondConfiguredMaximum() {
        LocalFileStorageSigner signer = signer();

        try {
            signer.presignDownload(new PresignDownloadRequest(ObjectKey.of("file.txt"),
                    Duration.ofHours(2), PresignDownloadRequest.ResponseDisposition.ATTACHMENT), "/files");
            fail("expected maximum expiry rejection");
        } catch (FileStorageException exception) {
            assertEquals("INVALID_PRESIGN", exception.errorCode().name());
        }
    }

    private static LocalFileStorageSigner signer() {
        return new LocalFileStorageSigner(KEY, Duration.ofHours(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            fail("expected invalid signature");
        } catch (FileStorageException exception) {
            assertEquals("INVALID_PRESIGN", exception.errorCode().name());
        }
    }

    private static String queryParameter(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("missing query parameter " + name);
    }
}
