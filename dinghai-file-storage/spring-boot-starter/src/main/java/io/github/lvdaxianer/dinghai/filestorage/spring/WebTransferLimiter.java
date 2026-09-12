package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 为 Web 上传和读取提供相互独立的非阻塞并发许可。 */
final class WebTransferLimiter {

    private final Semaphore uploadPermits;
    private final Semaphore readPermits;

    WebTransferLimiter(int maxConcurrentUploads, int maxConcurrentReads) {
        if (maxConcurrentUploads <= 0 || maxConcurrentReads <= 0) {
            throw new IllegalArgumentException("web transfer limits must be positive");
        }
        this.uploadPermits = new Semaphore(maxConcurrentUploads);
        this.readPermits = new Semaphore(maxConcurrentReads);
    }

    Permit acquireUpload() {
        return acquire(uploadPermits);
    }

    Permit acquireRead() {
        return acquire(readPermits);
    }

    private Permit acquire(Semaphore permits) {
        if (!permits.tryAcquire()) {
            throw new FileStorageException(FileStorageErrorCode.RATE_LIMITED,
                    "web transfer concurrency limit reached");
        }
        return new Permit(permits);
    }

    static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
