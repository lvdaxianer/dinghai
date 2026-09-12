package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 为 Web 上传和读取提供相互独立的非阻塞并发许可。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
final class WebTransferLimiter {

    private final Semaphore uploadPermits;
    private final Semaphore readPermits;
    private final int maxConcurrentTransfersPerKey;
    private final ConcurrentHashMap<String, KeySlot> uploadKeySlots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeySlot> readKeySlots = new ConcurrentHashMap<>();

    /**
     * 创建指定容量的上传和读取许可池。
     *
     * @param maxConcurrentUploads 最大上传并发数
     * @param maxConcurrentReads 最大读取并发数
     */
    WebTransferLimiter(int maxConcurrentUploads, int maxConcurrentReads) {
        this(maxConcurrentUploads, maxConcurrentReads, 0);
    }

    /**
     * 创建指定全局和每 key 容量的上传、读取许可池。
     *
     * @param maxConcurrentUploads 最大上传并发数
     * @param maxConcurrentReads 最大读取并发数
     * @param maxConcurrentTransfersPerKey 最大每 key 并发数，零表示关闭
     */
    WebTransferLimiter(int maxConcurrentUploads, int maxConcurrentReads,
                       int maxConcurrentTransfersPerKey) {
        if (maxConcurrentUploads <= 0 || maxConcurrentReads <= 0
                || maxConcurrentTransfersPerKey < 0) {
            throw new IllegalArgumentException("web transfer global limits must be positive and per-key limit must not be negative");
        }
        this.uploadPermits = new Semaphore(maxConcurrentUploads);
        this.readPermits = new Semaphore(maxConcurrentReads);
        this.maxConcurrentTransfersPerKey = maxConcurrentTransfersPerKey;
    }

    /**
     * 获取一个上传许可，容量耗尽时立即抛出稳定限流异常。
     *
     * @return 上传许可
     */
    Permit acquireUpload() {
        return acquireUpload(null);
    }

    /**
     * 获取一个上传许可，并在启用时获取指定 key 的上传许可。
     *
     * @param key 宿主解析的限流 key
     * @return 上传许可
     */
    Permit acquireUpload(String key) {
        return acquire(uploadPermits, uploadKeySlots, key);
    }

    /**
     * 获取一个读取许可，容量耗尽时立即抛出稳定限流异常。
     *
     * @return 读取许可
     */
    Permit acquireRead() {
        return acquireRead(null);
    }

    /**
     * 获取一个读取许可，并在启用时获取指定 key 的读取许可。
     *
     * @param key 宿主解析的限流 key
     * @return 读取许可
     */
    Permit acquireRead(String key) {
        return acquire(readPermits, readKeySlots, key);
    }

    /** 返回当前活跃的上传和读取 key 状态数，仅供包内测试验证回收行为。 */
    int activeKeyCount() {
        return uploadKeySlots.size() + readKeySlots.size();
    }

    private Permit acquire(Semaphore permits, ConcurrentHashMap<String, KeySlot> keySlots, String key) {
        if (!permits.tryAcquire()) {
            throw rateLimited();
        }
        if (maxConcurrentTransfersPerKey == 0 || key == null || key.isBlank()) {
            return new Permit(permits, null);
        }
        KeySlot slot = acquireKeySlot(keySlots, key);
        if (slot == null) {
            permits.release();
            throw rateLimited();
        }
        return new Permit(permits, () -> releaseKeySlot(keySlots, key, slot));
    }

    private KeySlot acquireKeySlot(ConcurrentHashMap<String, KeySlot> keySlots, String key) {
        AtomicReference<KeySlot> acquired = new AtomicReference<>();
        keySlots.compute(key, (ignored, existing) -> {
            KeySlot slot = existing == null ? new KeySlot(maxConcurrentTransfersPerKey) : existing;
            if (slot.tryAcquire()) {
                acquired.set(slot);
            }
            return slot;
        });
        return acquired.get();
    }

    private void releaseKeySlot(ConcurrentHashMap<String, KeySlot> keySlots, String key, KeySlot acquired) {
        keySlots.computeIfPresent(key, (ignored, existing) -> {
            if (existing == acquired) {
                existing.release();
                return existing.isIdle() ? null : existing;
            }
            return existing;
        });
    }

    private FileStorageException rateLimited() {
        return new FileStorageException(FileStorageErrorCode.RATE_LIMITED,
                "web transfer concurrency limit reached");
    }

    static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final Runnable releaseKey;
        private final AtomicBoolean released = new AtomicBoolean();

        /**
         * 创建绑定到指定许可池的可幂等释放许可。
         *
         * @param permits 许可池
         */
        private Permit(Semaphore permits, Runnable releaseKey) {
            this.permits = permits;
            this.releaseKey = releaseKey;
        }

        @Override
        /**
         * 释放许可；重复调用不会增加池容量。
         */
        public void close() {
            if (released.compareAndSet(false, true)) {
                try {
                    if (releaseKey != null) {
                        releaseKey.run();
                    }
                } finally {
                    permits.release();
                }
            }
        }
    }

    /** 单个 key 的活跃许可槽位。 */
    private static final class KeySlot {
        private final Semaphore permits;
        private int activeTransfers;

        /** 创建指定容量的 key 槽位。 */
        private KeySlot(int maxConcurrentTransfers) {
            this.permits = new Semaphore(maxConcurrentTransfers);
        }

        /** 尝试获取 key 许可。 */
        private boolean tryAcquire() {
            if (!permits.tryAcquire()) {
                return false;
            }
            activeTransfers++;
            return true;
        }

        /** 释放 key 许可。 */
        private void release() {
            activeTransfers--;
            permits.release();
        }

        /** 判断 key 槽位是否没有活跃传输。 */
        private boolean isIdle() {
            return activeTransfers == 0;
        }
    }
}
