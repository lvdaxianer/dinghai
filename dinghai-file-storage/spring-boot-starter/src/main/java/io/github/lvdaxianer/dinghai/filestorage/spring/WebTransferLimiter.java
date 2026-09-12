package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 为 Web 上传和读取提供相互独立的非阻塞并发许可。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
final class WebTransferLimiter {

    private final Semaphore uploadPermits;
    private final Semaphore readPermits;

    /**
     * 创建指定容量的上传和读取许可池。
     *
     * @param maxConcurrentUploads 最大上传并发数
     * @param maxConcurrentReads 最大读取并发数
     */
    WebTransferLimiter(int maxConcurrentUploads, int maxConcurrentReads) {
        if (maxConcurrentUploads <= 0 || maxConcurrentReads <= 0) {
            throw new IllegalArgumentException("web transfer limits must be positive");
        }
        this.uploadPermits = new Semaphore(maxConcurrentUploads);
        this.readPermits = new Semaphore(maxConcurrentReads);
    }

    /**
     * 获取一个上传许可，容量耗尽时立即抛出稳定限流异常。
     *
     * @return 上传许可
     */
    Permit acquireUpload() {
        return acquire(uploadPermits);
    }

    /**
     * 获取一个读取许可，容量耗尽时立即抛出稳定限流异常。
     *
     * @return 读取许可
     */
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

        /**
         * 创建绑定到指定许可池的可幂等释放许可。
         *
         * @param permits 许可池
         */
        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        /**
         * 释放许可；重复调用不会增加池容量。
         */
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
