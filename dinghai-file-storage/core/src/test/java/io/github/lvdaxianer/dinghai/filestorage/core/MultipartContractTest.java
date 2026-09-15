package io.github.lvdaxianer.dinghai.filestorage.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Test;

/** multipart 核心契约的值模型测试。 */
public class MultipartContractTest {

    @Test
    public void rejectsInvalidPartSizeAndTotalSize() {
        assertThrows(IllegalArgumentException.class, () -> request(0L, 1024L));
        assertThrows(IllegalArgumentException.class, () -> request(1024L, 0L));
    }

    @Test
    public void computesPartCountAndKeepsPartsImmutable() {
        MultipartUploadRequest request = request(2_500L, 1_000L);
        assertEquals(3, request.partCount());

        MultipartUploadSession session = new MultipartUploadSession("upload", request,
                Instant.parse("2026-09-15T00:00:00Z"));
        MultipartPart part = new MultipartPart(1, 1_000L, "etag");
        assertEquals(List.of(part), List.copyOf(session.withParts(List.of(part)).parts()));
        assertThrows(UnsupportedOperationException.class, () -> session.withParts(List.of(part)).parts().add(part));
    }

    @Test
    public void rejectsPartNumbersOutsideSessionBounds() {
        MultipartUploadRequest request = request(2_500L, 1_000L);
        assertThrows(IllegalArgumentException.class, () -> new MultipartPartRequest("upload", 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new MultipartPartRequest("upload", 4, 3));
        assertEquals(3, new MultipartPartRequest("upload", 3, request.partCount()).partNumber());
    }

    private static MultipartUploadRequest request(long size, long partSize) {
        return new MultipartUploadRequest(ObjectKey.of("large.bin"), size, partSize,
                "application/octet-stream", CacheControl.privateNoCache(), Duration.ofHours(1));
    }
}
