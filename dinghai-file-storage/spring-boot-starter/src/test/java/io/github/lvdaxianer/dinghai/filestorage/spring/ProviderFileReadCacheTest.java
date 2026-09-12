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

    /** 相同 key 的并发未命中只允许一个异步缓存填充任务。 */
    @Test
    public void shouldFillEachKeyOnlyOnceWhileCacheTaskIsInFlight() throws Exception {
        CountingStorage storage = new CountingStorage();
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);

        try (DownloadObject first = cache.get(KEY).orElseThrow();
             DownloadObject second = cache.get(KEY).orElseThrow()) {
            first.stream().readAllBytes();
            second.stream().readAllBytes();
        }
        cache.awaitIdle(2L, TimeUnit.SECONDS);

        assertEquals(3, storage.getCalls.get());
        cache.close();
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
        /** 当前对象元数据。 */
        private volatile ObjectMetadata metadata = METADATA;

        /** 返回独立输入流和当前元数据。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            if (!KEY.equals(key)) {
                return Optional.empty();
            }
            getCalls.incrementAndGet();
            byte[] value = metadata.contentLength() == 5L ? bytes("hello") : bytes("changed");
            return Optional.of(new DownloadObject(new ByteArrayInputStream(value), metadata));
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
