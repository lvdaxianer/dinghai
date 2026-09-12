package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Web 上传和读取并发许可测试。 */
public class WebTransferLimiterTest {
    @Test
    public void shouldRejectUploadWhenLimitReached() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1);
        WebTransferLimiter.Permit permit = limiter.acquireUpload();
        try {
            limiter.acquireUpload();
            fail("expected rate limit");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.RATE_LIMITED, exception.errorCode());
        } finally {
            permit.close();
        }
    }

    @Test
    public void shouldAllowUploadAfterPermitRelease() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1);
        WebTransferLimiter.Permit permit = limiter.acquireUpload();
        permit.close();
        WebTransferLimiter.Permit next = limiter.acquireUpload();
        next.close();
    }

    @Test
    public void shouldKeepUploadAndReadLimitsIndependent() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1);
        WebTransferLimiter.Permit upload = limiter.acquireUpload();
        WebTransferLimiter.Permit read = limiter.acquireRead();
        upload.close();
        read.close();
    }
}
