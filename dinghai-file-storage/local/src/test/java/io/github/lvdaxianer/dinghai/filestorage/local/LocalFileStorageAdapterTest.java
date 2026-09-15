package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.LocalFileSource;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** LocalFileStorageAdapter 的磁盘契约测试。 */
public class LocalFileStorageAdapterTest {
    private static final byte[] SIGNING_KEY = "local-test-signing-key-which-is-long-enough".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 多级数字 key 会安全映射到根目录下的嵌套路径。 */
    @Test
    public void writesNestedObjectUnderStorageRoot() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);
        ObjectKey key = ObjectKey.of("1/1/1/1.png");

        storage.put(upload(key, "image"));

        assertTrue(Files.isRegularFile(root.resolve("1/1/1/1.png")));
        assertEquals("image", read(storage.get(key)));
    }

    /** 可选本地文件来源只暴露根目录内已存在的普通对象文件。 */
    @Test
    public void exposesExistingObjectAsSafeLocalFileSource() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);
        ObjectKey key = ObjectKey.of("1/1/1/1.png");
        storage.put(upload(key, "image"));

        LocalFileSource source = storage;

        assertEquals(root.resolve(key.value()).toRealPath(), source.localFile(key).orElseThrow());
        assertTrue(source.localFile(ObjectKey.of("missing.png")).isEmpty());
    }

    /** 中文 key 可往返内容与写入时的元数据。 */
    @Test
    public void roundTripsUnicodeKeyAndMetadata() throws Exception {
        LocalFileStorageAdapter storage = storage();
        ObjectKey key = ObjectKey.of("用户资料/头像.png");
        CacheControl cacheControl = new CacheControl("public, max-age=60");

        ObjectMetadata written = storage.put(new UploadRequest(key,
                new ByteArrayInputStream("头像".getBytes(StandardCharsets.UTF_8)), 6,
                "image/png", cacheControl));

        ObjectMetadata stat = storage.stat(key).orElseThrow();
        assertEquals("image/png", stat.contentType());
        assertEquals(cacheControl, stat.cacheControl());
        assertEquals(written.etag(), stat.etag());
        assertEquals("头像", read(storage.get(key)));
    }

    /** 覆盖同一对象会替换内容和 ETag。 */
    @Test
    public void overwritingObjectReplacesContentAndEtag() throws Exception {
        LocalFileStorageAdapter storage = storage();
        ObjectKey key = ObjectKey.of("documents/current.txt");
        String firstEtag = storage.put(upload(key, "first")).etag();

        String secondEtag = storage.put(upload(key, "second")).etag();

        assertNotEquals(firstEtag, secondEtag);
        assertEquals("second", read(storage.get(key)));
    }

    /** 删除不存在对象是幂等的，并不会制造元数据文件。 */
    @Test
    public void deletingMissingObjectReturnsFalse() throws Exception {
        LocalFileStorageAdapter storage = storage();

        assertFalse(storage.delete(ObjectKey.of("missing/file.txt")));
    }

    /** 不安全 key 在接触磁盘前被拒绝。 */
    @Test
    public void rejectsUnsafeKeysBeforeFilesystemAccess() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);

        for (String raw : new String[]{"../secret.txt", "tenant/../../secret.txt", "/tmp/a", "a\\b"}) {
            try {
                storage.put(upload(ObjectKey.of(raw), "unsafe"));
                fail("expected unsafe key rejection for " + raw);
            } catch (IllegalArgumentException expected) {
                assertFalse(Files.exists(root.resolve("secret.txt")));
            }
        }
    }

    /** 已存在的中间符号链接不能将新对象写入根目录之外。 */
    @Test
    public void rejectsIntermediateSymbolicLink() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(root.resolve("linked"), outside);
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);

        try {
            storage.put(upload(ObjectKey.of("linked/file.txt"), "unsafe"));
            fail("expected symbolic link rejection");
        } catch (FileStorageException expected) {
            assertFalse(Files.exists(outside.resolve("file.txt")));
        }
    }

    /** 缺失内容元数据采用安全的读取默认值。 */
    @Test
    public void missingMetadataUsesSafeDefaults() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        Files.createDirectories(root.resolve("legacy"));
        Files.writeString(root.resolve("legacy/file.bin"), "legacy", StandardCharsets.UTF_8);
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);

        ObjectMetadata metadata = storage.stat(ObjectKey.of("legacy/file.bin")).orElseThrow();

        assertEquals("application/octet-stream", metadata.contentType());
        assertEquals(CacheControl.privateNoCache(), metadata.cacheControl());
        assertEquals(6L, metadata.contentLength());
    }

    /** 超出本地上传限制不会覆盖既有对象。 */
    @Test
    public void rejectsOversizedStreamWithoutReplacingExistingObject() throws Exception {
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(
                temporaryFolder.newFolder("storage").toPath(), SIGNING_KEY, 5);
        ObjectKey key = ObjectKey.of("safe.txt");
        storage.put(upload(key, "safe"));

        try {
            storage.put(upload(key, "too-large"));
            fail("expected size validation failure");
        } catch (FileStorageException exception) {
            assertEquals("OBJECT_TOO_LARGE", exception.errorCode().name());
        }
        assertEquals("safe", read(storage.get(key)));
    }

    /** 被声明大小提前拒绝的流仍由存储实现关闭。 */
    @Test
    public void closesRejectedUploadStream() throws Exception {
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(
                temporaryFolder.newFolder("storage").toPath(), SIGNING_KEY, 1);
        CloseTrackingInputStream input = new CloseTrackingInputStream();

        try {
            storage.put(new UploadRequest(ObjectKey.of("too-large.bin"), input, 2,
                    "application/octet-stream"));
            fail("expected size validation failure");
        } catch (FileStorageException expected) {
            assertTrue(input.closed);
        }
    }

    /** 普通上传和预签名上传共用内容类型白名单。 */
    @Test
    public void enforcesContentTypeWhitelistForUploadPaths() throws Exception {
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(
                temporaryFolder.newFolder("storage").toPath(), SIGNING_KEY, 1024,
                Set.of("image/png"), LocalFileStorageAdapter.DEFAULT_MAXIMUM_PRESIGN_EXPIRY,
                java.time.Clock.systemUTC());

        try {
            storage.put(upload(ObjectKey.of("unsafe.txt"), "not allowed"));
            fail("expected content type rejection");
        } catch (FileStorageException exception) {
            assertEquals("CONTENT_TYPE_NOT_ALLOWED", exception.errorCode().name());
        }
        try {
            storage.presignUpload(new io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest(
                    ObjectKey.of("unsafe.txt"), java.time.Duration.ofMinutes(1), "text/plain", 100));
            fail("expected presign content type rejection");
        } catch (FileStorageException exception) {
            assertEquals("CONTENT_TYPE_NOT_ALLOWED", exception.errorCode().name());
        }
    }

    /** 枚举必须产生对象元数据并在关闭时释放文件系统遍历资源。 */
    @Test
    public void listsObjectMetadataThroughClosableStream() throws Exception {
        LocalFileStorageAdapter storage = storage();
        storage.put(upload(ObjectKey.of("documents/a.txt"), "a"));
        storage.put(upload(ObjectKey.of("用户资料/b.txt"), "b"));

        try (Stream<ObjectMetadata> objects = storage.list()) {
            assertEquals(Set.of("documents/a.txt", "用户资料/b.txt"), objects
                    .map(metadata -> metadata.key().value()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    /** 枚举忽略存储根内的符号链接，避免把根外文件视为对象。 */
    @Test
    public void ignoresSymbolicLinksWhenListingObjects() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        Path outside = temporaryFolder.newFile("outside.txt").toPath();
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY);
        storage.put(upload(ObjectKey.of("documents/a.txt"), "a"));
        Files.createSymbolicLink(root.resolve("linked.txt"), outside);

        try (Stream<ObjectMetadata> objects = storage.list()) {
            assertEquals(Set.of("documents/a.txt"), objects.map(metadata -> metadata.key().value())
                    .collect(java.util.stream.Collectors.toSet()));
        }
    }

    /** 分片会话和分片内容可在适配器重启后恢复。 */
    @Test
    public void persistsMultipartPartsAcrossAdapterRestart() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        LocalFileStorageAdapter first = new LocalFileStorageAdapter(root, SIGNING_KEY);
        MultipartUploadSession session = first.initiate(multipartRequest("restart.bin", 6));
        MultipartPart uploaded = first.uploadPart(new MultipartPartRequest(session.uploadId(), 1),
                bytes("abc"), 3);
        MultipartPart second = first.uploadPart(new MultipartPartRequest(session.uploadId(), 2),
                bytes("def"), 3);

        LocalFileStorageAdapter restarted = new LocalFileStorageAdapter(root, SIGNING_KEY);

        assertEquals(List.of(uploaded, second), restarted.listParts(session.uploadId()));
        ObjectMetadata completed = restarted.complete(session.uploadId(),
                new MultipartCompletion(List.of(uploaded, second)));
        assertEquals(6L, completed.contentLength());
    }

    /** 同一分片重试使用原子替换，最终只保留最新内容。 */
    @Test
    public void retriesMultipartPartByAtomicReplacement() throws Exception {
        LocalFileStorageAdapter storage = storage();
        MultipartUploadSession session = storage.initiate(multipartRequest("retry.bin", 6));
        MultipartPart first = storage.uploadPart(new MultipartPartRequest(session.uploadId(), 1),
                bytes("old"), 3);
        MultipartPart second = storage.uploadPart(new MultipartPartRequest(session.uploadId(), 1),
                bytes("new"), 3);

        assertNotEquals(first.etag(), second.etag());
        assertEquals(List.of(second), storage.listParts(session.uploadId()));
    }

    /** 完成操作按分片顺序流式合并并可幂等重复提交。 */
    @Test
    public void completesMultipartByStreamingMergeAndIsIdempotent() throws Exception {
        LocalFileStorageAdapter storage = storage();
        MultipartUploadSession session = storage.initiate(multipartRequest("merged.bin", 6));
        MultipartPart first = storage.uploadPart(new MultipartPartRequest(session.uploadId(), 1),
                bytes("abc"), 3);
        MultipartPart second = storage.uploadPart(new MultipartPartRequest(session.uploadId(), 2),
                bytes("def"), 3);

        ObjectMetadata completed = storage.complete(session.uploadId(),
                new MultipartCompletion(List.of(first, second)));
        ObjectMetadata repeated = storage.complete(session.uploadId(),
                new MultipartCompletion(List.of(first, second)));

        assertEquals(6L, completed.contentLength());
        assertEquals(completed.etag(), repeated.etag());
        assertEquals("abcdef", read(storage.get(ObjectKey.of("merged.bin"))));
    }

    /** 缺少分片时完成必须返回稳定冲突错误。 */
    @Test
    public void rejectsMultipartCompletionWithMissingPart() throws Exception {
        LocalFileStorageAdapter storage = storage();
        MultipartUploadSession session = storage.initiate(multipartRequest("missing.bin", 6));
        MultipartPart first = storage.uploadPart(new MultipartPartRequest(session.uploadId(), 1),
                bytes("abc"), 3);

        try {
            storage.complete(session.uploadId(), new MultipartCompletion(List.of(first)));
            fail("expected missing part conflict");
        } catch (FileStorageException exception) {
            assertEquals("MULTIPART_CONFLICT", exception.errorCode().name());
        }
    }

    /** 取消会话具备幂等语义并移除临时目录。 */
    @Test
    public void abortsMultipartSessionIdempotently() throws Exception {
        LocalFileStorageAdapter storage = storage();
        MultipartUploadSession session = storage.initiate(multipartRequest("abort.bin", 3));

        storage.abort(session.uploadId());
        storage.abort(session.uploadId());

        try {
            storage.listParts(session.uploadId());
            fail("expected aborted session to be absent");
        } catch (FileStorageException exception) {
            assertEquals("MULTIPART_NOT_FOUND", exception.errorCode().name());
        }
    }

    /** 过期会话清理不会影响已完成对象。 */
    @Test
    public void cleansExpiredMultipartSessionsWithoutDeletingObjects() throws Exception {
        Path root = temporaryFolder.newFolder("storage").toPath();
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
        LocalFileStorageAdapter storage = new LocalFileStorageAdapter(root, SIGNING_KEY,
                UploadRequest.DEFAULT_MAX_SIZE_BYTES, Set.of(),
                LocalFileStorageAdapter.DEFAULT_MAXIMUM_PRESIGN_EXPIRY, clock);
        MultipartUploadSession session = storage.initiate(new MultipartUploadRequest(
                ObjectKey.of("expired.bin"), 3, 3, "text/plain", null, Duration.ofSeconds(1)));
        storage.put(upload(ObjectKey.of("kept.bin"), "kept"));

        assertEquals(1, storage.cleanupExpiredMultipartSessions(clock.instant().plusSeconds(2)));
        try {
            storage.listParts(session.uploadId());
            fail("expected expired session to be absent");
        } catch (FileStorageException exception) {
            assertEquals("MULTIPART_NOT_FOUND", exception.errorCode().name());
        }
        assertTrue(storage.exists(ObjectKey.of("kept.bin")));
    }

    private LocalFileStorageAdapter storage() throws Exception {
        return new LocalFileStorageAdapter(temporaryFolder.newFolder("storage").toPath(), SIGNING_KEY);
    }

    private static UploadRequest upload(ObjectKey key, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new UploadRequest(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
    }

    private static MultipartUploadRequest multipartRequest(String key, long totalSize) {
        return new MultipartUploadRequest(ObjectKey.of(key), totalSize, 3,
                "text/plain", null, Duration.ofMinutes(10));
    }

    private static InputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Optional<DownloadObject> object) throws Exception {
        try (DownloadObject download = object.orElseThrow()) {
            return new String(download.stream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 记录输入流是否已由被测存储关闭。 */
    private static final class CloseTrackingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
