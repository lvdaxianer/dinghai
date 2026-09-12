package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 非本地 provider 二级磁盘缓存的契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class ProviderFileReadCacheTest {
    /** 测试异步等待超时。 */
    private static final long TEST_TIMEOUT_SECONDS = 2L;
    /** 测试对象 key。 */
    private static final ObjectKey KEY = ObjectKey.of("reports/cache.txt");
    /** 测试对象元数据。 */
    private static final ObjectMetadata METADATA = new ObjectMetadata(KEY, 5L, "text/plain",
            "etag-1", Instant.parse("2026-09-11T00:00:00Z"), CacheControl.privateNoCache());

    /** 测试缓存目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 缓存未命中时应先返回 provider 流，并异步落地独立副本。 */
    @Test
    public void shouldPassThroughAndFillCacheWithSecondProviderStream() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);

        try (DownloadObject object = cache.get(KEY).orElseThrow()) {
            assertEquals("hello", new String(object.stream().readAllBytes(), StandardCharsets.UTF_8));
        }
        cache.awaitIdle(2L, TimeUnit.SECONDS);

        assertEquals(2, storage.getCalls.get());
        assertTrue(cache.hasCachedObject(KEY));
        cache.close();
    }

    /** 仅完整且元数据匹配的缓存副本可作为本地文件传输来源。 */
    @Test
    public void shouldExposeVerifiedCacheHitAsLocalFile() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);
        try (DownloadObject ignored = cache.get(KEY).orElseThrow()) {
            ignored.stream().readAllBytes();
        }
        cache.awaitIdle(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Path localFile = cache.localFile(KEY).orElseThrow();

        assertTrue(Files.isRegularFile(localFile));
        assertEquals("hello", Files.readString(localFile));
        cache.close();
    }

    /** 元数据不匹配时必须丢弃旧副本并重新读取 provider。 */
    @Test
    public void shouldInvalidateCacheWhenRemoteMetadataChanges() throws Exception {
        CountingStorage storage = new CountingStorage();
        Path root = temporaryFolder.newFolder("cache").toPath();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents", root, true, 1024L);
        try (DownloadObject ignored = cache.get(KEY).orElseThrow()) {
            ignored.stream().readAllBytes();
        }
        cache.awaitIdle(2L, TimeUnit.SECONDS);
        storage.metadata = new ObjectMetadata(KEY, 6L, "text/plain", "etag-2",
                METADATA.lastModified().plusSeconds(1L), CacheControl.privateNoCache());

        try (DownloadObject object = cache.get(KEY).orElseThrow()) {
            assertEquals("changed", new String(object.stream().readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(3, storage.getCalls.get());
        cache.close();
    }

    /** 超过阈值的对象不得进入缓存，但当前读取仍保持流式。 */
    @Test
    public void shouldSkipCacheForObjectsAboveThreshold() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 4L);

        try (DownloadObject object = cache.get(KEY).orElseThrow()) {
            assertEquals("hello", new String(object.stream().readAllBytes(), StandardCharsets.UTF_8));
        }
        cache.awaitIdle(1L, TimeUnit.SECONDS);

        assertEquals(1, storage.getCalls.get());
        assertTrue(!cache.hasCachedObject(KEY));
        cache.close();
    }

    /** provider 实际流超过元数据长度时不得发布超长缓存。 */
    @Test
    public void shouldRejectProviderStreamLongerThanMetadata() throws Exception {
        CountingStorage storage = new CountingStorage();
        storage.overrideBytes = "hello-too-long".getBytes(StandardCharsets.UTF_8);
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);
        try (DownloadObject object = cache.get(KEY).orElseThrow()) {
            object.stream().readAllBytes();
        }
        cache.awaitIdle(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(!cache.hasCachedObject(KEY));
        cache.close();
    }

    /** 相同 key 的并发未命中只允许一个异步缓存填充任务。 */
    @Test
    public void shouldFillEachKeyOnlyOnceWhileCacheTaskIsInFlight() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);
        storage.blockFillStream();
        try {
            try (DownloadObject first = cache.get(KEY).orElseThrow()) {
                first.stream().readAllBytes();
            }
            assertTrue(storage.awaitFillStreamStart(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            try (DownloadObject second = cache.get(KEY).orElseThrow()) {
                assertEquals("hello", new String(second.stream().readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            storage.releaseFillStream();
            cache.awaitIdle(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            cache.close();
        }
        assertEquals(3, storage.getCalls.get());
    }

    /** 超过空闲期限的完整缓存副本和 sidecar 必须一同删除。 */
    @Test
    public void shouldRemoveExpiredCacheCopies() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);
        try (DownloadObject object = cache.get(KEY).orElseThrow()) {
            object.stream().readAllBytes();
        }
        cache.awaitIdle(2L, TimeUnit.SECONDS);

        cache.cleanExpired(Instant.now().plus(Duration.ofDays(2L)), Duration.ofDays(1L));

        assertTrue(!cache.hasCachedObject(KEY));
        cache.close();
    }

    /** 测试用远程文件存储替身。 */
    private static final class CountingStorage implements FileStorage {
        /** get 调用次数。 */
        private final AtomicInteger getCalls = new AtomicInteger();
        /** 缓存填充流开始读取的信号。 */
        private final CountDownLatch fillStreamStarted = new CountDownLatch(1);
        /** 允许缓存填充流继续读取的信号。 */
        private final CountDownLatch fillStreamRelease = new CountDownLatch(1);
        /** 当前对象元数据。 */
        private volatile ObjectMetadata metadata = METADATA;
        /** 是否阻塞缓存填充流。 */
        private volatile boolean isFillStreamBlocked;
        /** 可选的故意超长 provider 数据。 */
        private volatile byte[] overrideBytes;

        /** 阻塞第二次 get 返回的缓存填充流。 */
        private void blockFillStream() { isFillStreamBlocked = true; }

        /** 等待缓存填充任务开始读取其独立流。 */
        private boolean awaitFillStreamStart(long timeout, TimeUnit unit) throws InterruptedException {
            return fillStreamStarted.await(timeout, unit);
        }

        /** 解除缓存填充流阻塞。 */
        private void releaseFillStream() { fillStreamRelease.countDown(); }

        /** 返回独立输入流和当前元数据。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            if (!KEY.equals(key)) {
                return Optional.empty();
            }
            int getCall = getCalls.incrementAndGet();
            byte[] value = overrideBytes != null ? overrideBytes
                    : (metadata.contentLength() == 5L ? bytes("hello") : bytes("changed"));
            InputStream stream = new ByteArrayInputStream(value);
            if (isFillStreamBlocked && getCall == 2) {
                stream = blockingFillStream(stream);
            }
            return Optional.of(new DownloadObject(stream, metadata));
        }

        /** 返回会在首次读取前等待测试释放的缓存填充流。 */
        private InputStream blockingFillStream(InputStream source) {
            return new InputStream() {
                /** 首次读取前等待测试线程确认并发窗口。 */
                private void awaitRelease() throws java.io.IOException {
                    fillStreamStarted.countDown();
                    try {
                        if (!fillStreamRelease.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new java.io.IOException("cache fill stream was not released");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException("cache fill stream was interrupted", exception);
                    }
                }

                /** 阻塞后读取一个字节。 */
                @Override public int read() throws java.io.IOException {
                    awaitRelease();
                    return source.read();
                }

                /** 阻塞后批量读取字节。 */
                @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                    awaitRelease();
                    return source.read(buffer, offset, length);
                }

                /** 关闭底层测试流。 */
                @Override public void close() throws java.io.IOException { source.close(); }
            };
        }

        /** 返回当前远程元数据。 */
        @Override
        public Optional<ObjectMetadata> stat(ObjectKey key) {
            return KEY.equals(key) ? Optional.of(metadata) : Optional.empty();
        }

        /** 测试替身不支持写入。 */
        @Override public ObjectMetadata put(UploadRequest request) { throw new UnsupportedOperationException(); }
        /** 测试替身判断对象存在。 */
        @Override public boolean exists(ObjectKey key) { return stat(key).isPresent(); }
        /** 测试替身不支持删除。 */
        @Override public boolean delete(ObjectKey key) { throw new UnsupportedOperationException(); }
        /** 测试替身返回对象枚举。 */
        @Override public Stream<ObjectMetadata> list() { return Stream.of(metadata); }
        /** 测试替身不支持预签名上传。 */
        @Override public PresignedUpload presignUpload(PresignUploadRequest request) { throw new UnsupportedOperationException(); }
        /** 测试替身不支持预签名下载。 */
        @Override public PresignedDownload presignDownload(PresignDownloadRequest request) { throw new UnsupportedOperationException(); }

        /** 将测试文本编码为 UTF-8。 */
        private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    }
}
