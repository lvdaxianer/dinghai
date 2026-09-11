package io.github.lvdaxianer.dinghai.filestorage.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 文件存储公共模型和契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageContractModelTest {

    /** 默认上传上限为 100 MB，缺失类型规范化为二进制类型。 */
    @Test
    public void appliesUploadDefaultsAndLimit() {
        UploadRequest request = UploadRequest.of(ObjectKey.of("1/1/1.png"),
                new ByteArrayInputStream(new byte[] {1, 2}), 2, null);
        assertEquals("application/octet-stream", request.contentType());
        assertEquals(100L * 1024 * 1024, UploadRequest.DEFAULT_MAX_SIZE_BYTES);
        assertEquals(CacheControl.privateNoCache(), request.cacheControl());
    }

    /** 超过默认大小的已知长度上传应通过统一校验失败。 */
    @Test(expected = IllegalArgumentException.class)
    public void rejectsUploadOverDefaultLimit() {
        UploadRequest request = UploadRequest.of(ObjectKey.of("large.bin"),
                new ByteArrayInputStream(new byte[0]),
                UploadRequest.DEFAULT_MAX_SIZE_BYTES + 1, "application/octet-stream");
        request.validateDefault();
    }

    /** 非空白内容类型白名单仅允许匹配项。 */
    @Test
    public void validatesContentTypeWhitelist() {
        UploadRequest request = UploadRequest.of(ObjectKey.of("image.png"),
                new ByteArrayInputStream(new byte[] {1}), 1, "image/png");
        request.validate(1024, java.util.Set.of("image/png", "image/jpeg"));
        try {
            request.validate(1024, java.util.Set.of("image/jpeg"));
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected content type rejection");
    }

    /** 下载对象关闭时必须拥有并关闭底层流。 */
    @Test
    public void closesDownloadStream() throws Exception {
        TrackingInputStream input = new TrackingInputStream();
        ObjectMetadata metadata = new ObjectMetadata(ObjectKey.of("a.txt"), 0,
                "text/plain", "etag", Instant.now(), CacheControl.privateNoCache());
        DownloadObject object = new DownloadObject(input, metadata);
        assertNotNull(object.stream());
        object.close();
        assertTrue(input.closed);
    }

    /** 预签名模型必须保留方法、过期时间和请求约束。 */
    @Test
    public void carriesPresignConstraints() {
        PresignUploadRequest upload = new PresignUploadRequest(ObjectKey.of("a.txt"),
                Duration.ofMinutes(5), "text/plain", 123, CacheControl.privateNoCache());
        PresignedUpload result = new PresignedUpload("https://example.test/upload", "PUT",
                Instant.now().plus(upload.expiresIn()),
                Map.of("Content-Type", "text/plain"), Map.of());
        assertEquals("PUT", result.method());
        assertEquals("text/plain", result.requiredHeaders().get("Content-Type"));
        assertFalse(result.url().isBlank());
    }

    /** 文件存储异常提供稳定错误码但不强制暴露后端细节。 */
    @Test
    public void exposesStableErrorCode() {
        FileStorageException exception = new FileStorageException(
                FileStorageErrorCode.OBJECT_NOT_FOUND, "object missing", new IOException("backend"));
        assertEquals(FileStorageErrorCode.OBJECT_NOT_FOUND, exception.errorCode());
        assertEquals("object missing", exception.getMessage());
    }

    /** 测试用可观测关闭流。 */
    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
