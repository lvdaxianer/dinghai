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

    @Test
    public void shouldRejectBeforeCallerReadsRequestBody() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1);
        WebTransferLimiter.Permit held = limiter.acquireUpload();
        boolean[] bodyRead = {false};
        try {
            limiter.acquireUpload();
            bodyRead[0] = true;
            fail("expected rate limit");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.RATE_LIMITED, exception.errorCode());
            assertEquals(false, bodyRead[0]);
        } finally {
            held.close();
        }
    }

    @Test
    public void shouldRejectSameKeyWhenKeyLimitReached() {
        WebTransferLimiter limiter = new WebTransferLimiter(2, 2, 1);
        WebTransferLimiter.Permit permit = limiter.acquireUpload("tenant-a");
        try {
            limiter.acquireUpload("tenant-a");
            fail("expected key rate limit");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.RATE_LIMITED, exception.errorCode());
        } finally {
            permit.close();
        }
    }

    @Test
    public void shouldAllowDifferentKeysWithinGlobalLimit() {
        WebTransferLimiter limiter = new WebTransferLimiter(2, 2, 1);
        WebTransferLimiter.Permit first = limiter.acquireRead("tenant-a");
        WebTransferLimiter.Permit second = limiter.acquireRead("tenant-b");
        first.close();
        second.close();
    }

    @Test
    public void shouldKeepSameKeyUploadAndReadLimitsIndependent() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1, 1);
        WebTransferLimiter.Permit upload = limiter.acquireUpload("tenant-a");
        WebTransferLimiter.Permit read = limiter.acquireRead("tenant-a");
        upload.close();
        read.close();
    }

    @Test
    public void shouldReleaseGlobalPermitWhenKeyLimitRejectsRequest() {
        WebTransferLimiter limiter = new WebTransferLimiter(2, 1, 1);
        WebTransferLimiter.Permit held = limiter.acquireUpload("tenant-a");
        try {
            limiter.acquireUpload("tenant-a");
            fail("expected key rate limit");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.RATE_LIMITED, exception.errorCode());
        }
        WebTransferLimiter.Permit otherKey = limiter.acquireUpload("tenant-b");
        held.close();
        otherKey.close();
    }

    @Test
    public void shouldRemoveKeyStateAfterLastPermitRelease() {
        WebTransferLimiter limiter = new WebTransferLimiter(1, 1, 1);
        WebTransferLimiter.Permit permit = limiter.acquireRead("tenant-a");
        assertEquals(1, limiter.activeKeyCount());
        permit.close();
        assertEquals(0, limiter.activeKeyCount());
    }
}
