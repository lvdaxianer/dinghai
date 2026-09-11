package io.github.lvdaxianer.dinghai.filestorage.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 服务器目录迁移包导出的契约测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageMigrationServiceTest {
    /** 测试临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 导出包应包含对象文件和完整 NDJSON 元数据。 */
    @Test
    public void shouldExportStreamingManifestAndObjectFiles() throws Exception {
        Path exportRoot = temporaryFolder.newFolder("exports").toPath();
        ObjectKey key = ObjectKey.of("1/1/1/1.png");
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata metadata = new ObjectMetadata(key, content.length, "image/png", "etag-1",
                Instant.parse("2026-09-11T00:00:00Z"), new CacheControl("private, max-age=60"));
        FileStorageMigrationService service = new FileStorageMigrationService(
                new SingleObjectStorage(metadata, content), "documents", exportRoot, new ObjectMapper());

        Path packagePath = service.exportPackage("release_001");

        assertTrue(Files.isRegularFile(packagePath.resolve(FileStorageMigrationService.MANIFEST_NAME)));
        String manifest = Files.readString(packagePath.resolve(FileStorageMigrationService.MANIFEST_NAME));
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(manifest);
        assertEquals(1, node.get("version").asInt());
        assertEquals("documents", node.get("storageAlias").asText());
        assertEquals(key.value(), node.get("key").get("value").asText());
        assertEquals(content.length, node.get("contentLength").asLong());
        assertEquals("image/png", node.get("contentType").asText());
        assertEquals("private, max-age=60", node.get("cacheControl").get("value").asText());
        assertEquals("d2dfc251c1a7245d4eb7d95e5f815472c6dbcf7ee6690bbd7c1912f477b6c22a",
                node.get("sha256").asText());
        assertEquals(content.length, Files.size(packagePath.resolve(node.get("relativePath").asText())));
    }

    /** 非法包名必须在接触文件系统前拒绝。 */
    @Test
    public void shouldRejectUnsafePackageNames() throws Exception {
        FileStorageMigrationService service = new FileStorageMigrationService(
                new SingleObjectStorage(null, new byte[0]), "documents",
                temporaryFolder.newFolder("exports").toPath(), new ObjectMapper());
        for (String packageName : new String[]{"../escape", ".hidden", "with space", ""}) {
            try {
                service.exportPackage(packageName);
                fail("expected invalid package name: " + packageName);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("package name"));
            }
        }
    }

    /** 已存在且摘要相同的对象应幂等跳过，并将包移动到完成目录。 */
    @Test
    public void shouldSkipIdenticalTargetAndMovePackageToCompleted() throws Exception {
        Path exportRoot = temporaryFolder.newFolder("exports").toPath();
        Path importRoot = temporaryFolder.newFolder("imports").toPath();
        Path completedRoot = temporaryFolder.newFolder("completed").toPath();
        Path failedRoot = temporaryFolder.newFolder("failed").toPath();
        ObjectKey key = ObjectKey.of("reports/report.txt");
        byte[] content = "same".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata metadata = new ObjectMetadata(key, content.length, "text/plain",
                "remote-etag", Instant.parse("2026-09-11T00:00:00Z"));
        SingleObjectStorage storage = new SingleObjectStorage(metadata, content);
        FileStorageMigrationService service = new FileStorageMigrationService(storage, "documents",
                exportRoot, new ObjectMapper());
        Path exported = service.exportPackage("same-package");
        Files.move(exported, importRoot.resolve("same-package"));

        Path completed = service.importPackage("same-package", importRoot, completedRoot, failedRoot);

        assertEquals(completedRoot.resolve("same-package"), completed);
        assertTrue(Files.exists(completed.resolve(FileStorageMigrationService.MANIFEST_NAME)));
    }

    /** 摘要冲突必须失败、记录错误并移动完整包。 */
    @Test
    public void shouldMoveConflictingPackageToFailedWithErrorManifest() throws Exception {
        Path exportRoot = temporaryFolder.newFolder("exports").toPath();
        Path importRoot = temporaryFolder.newFolder("imports").toPath();
        Path completedRoot = temporaryFolder.newFolder("completed").toPath();
        Path failedRoot = temporaryFolder.newFolder("failed").toPath();
        ObjectKey key = ObjectKey.of("reports/report.txt");
        byte[] source = "source".getBytes(StandardCharsets.UTF_8);
        byte[] target = "target".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata sourceMetadata = new ObjectMetadata(key, source.length, "text/plain",
                "source-etag", Instant.parse("2026-09-11T00:00:00Z"));
        FileStorageMigrationService service = new FileStorageMigrationService(
                new SingleObjectStorage(sourceMetadata, source), "documents", exportRoot, new ObjectMapper());
        Path exported = service.exportPackage("conflict-package");
        Files.move(exported, importRoot.resolve("conflict-package"));
        SingleObjectStorage targetStorage = new SingleObjectStorage(
                new ObjectMetadata(key, target.length, "text/plain", "target-etag",
                        Instant.parse("2026-09-11T00:00:00Z")), target);
        FileStorageMigrationService importing = new FileStorageMigrationService(targetStorage,
                "documents", exportRoot, new ObjectMapper());

        try {
            importing.importPackage("conflict-package", importRoot, completedRoot, failedRoot);
            fail("expected migration conflict");
        } catch (IllegalStateException expected) {
            Path errors = failedRoot.resolve("conflict-package/errors.ndjson");
            assertTrue(Files.isRegularFile(errors));
            com.fasterxml.jackson.databind.JsonNode error = new ObjectMapper()
                    .readTree(Files.readString(errors));
            assertEquals("MIGRATION_OBJECT_CONFLICT", error.get("code").asText());
            assertEquals("manifest:1", error.get("source").asText());
        }
    }

    /** 不支持原子移动时，导出仍应以安全目录移动发布完整迁移包。 */
    @Test
    public void shouldPublishExportWhenAtomicDirectoryMoveIsUnsupported() throws Exception {
        Path exportRoot = temporaryFolder.newFolder("exports").toPath();
        ObjectKey key = ObjectKey.of("reports/report.txt");
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata metadata = new ObjectMetadata(key, content.length, "text/plain", "etag",
                Instant.parse("2026-09-12T00:00:00Z"));
        FileStorageMigrationService service = new FileStorageMigrationService(
                new SingleObjectStorage(metadata, content), "documents", exportRoot,
                new ObjectMapper(), false);

        Path packagePath = service.exportPackage("fallback-package");

        assertTrue(Files.isRegularFile(packagePath.resolve(FileStorageMigrationService.MANIFEST_NAME)));
        assertTrue(Files.isDirectory(packagePath.resolve(FileStorageMigrationService.OBJECTS_DIRECTORY)));
    }

    /** 后续对象校验失败时，导入不得写入先前已校验的对象。 */
    @Test
    public void shouldValidateEntirePackageBeforeWritingAnyTargetObject() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LocalFileStorageAdapter source = localStorage(root.resolve("source"));
        ObjectKey firstKey = ObjectKey.of("reports/first.txt");
        ObjectKey secondKey = ObjectKey.of("reports/second.txt");
        put(source, firstKey, "first");
        put(source, secondKey, "second");
        FileStorageMigrationService exporter = new FileStorageMigrationService(source, "documents",
                root.resolve("exports"), new ObjectMapper());
        Path packagePath = exporter.exportPackage("invalid-package");
        Path importRoot = root.resolve("imports");
        Files.createDirectories(importRoot);
        Files.move(packagePath, importRoot.resolve("invalid-package"));
        corruptSecondObject(importRoot.resolve("invalid-package"));
        LocalFileStorageAdapter target = localStorage(root.resolve("target"));
        FileStorageMigrationService importer = new FileStorageMigrationService(target, "documents",
                root.resolve("unused"), new ObjectMapper());

        try {
            importer.importPackage("invalid-package", importRoot, root.resolve("completed"),
                    root.resolve("failed"));
            fail("expected migration validation failure");
        } catch (IllegalStateException expected) {
            assertTrue(target.stat(firstKey).isEmpty());
            assertTrue(target.stat(secondKey).isEmpty());
        }
    }

    /** manifest 对象路径必须与对象 key 的摘要文件名精确匹配。 */
    @Test
    public void shouldRejectManifestObjectPathThatDoesNotMatchKeyDigest() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        LocalFileStorageAdapter source = localStorage(root.resolve("source-path"));
        ObjectKey key = ObjectKey.of("reports/report.txt");
        put(source, key, "content");
        FileStorageMigrationService exporter = new FileStorageMigrationService(source, "documents",
                root.resolve("exports-path"), new ObjectMapper());
        Path packagePath = exporter.exportPackage("invalid-path-package");
        Path importRoot = root.resolve("imports-path");
        Files.createDirectories(importRoot);
        Path importedPackage = importRoot.resolve("invalid-path-package");
        Files.move(packagePath, importedPackage);
        replaceRelativePath(importedPackage, "objects/other.bin");
        LocalFileStorageAdapter target = localStorage(root.resolve("target-path"));
        FileStorageMigrationService importer = new FileStorageMigrationService(target, "documents",
                root.resolve("unused-path"), new ObjectMapper());

        try {
            importer.importPackage("invalid-path-package", importRoot, root.resolve("completed-path"),
                    root.resolve("failed-path"));
            fail("expected migration object path failure");
        } catch (IllegalStateException expected) {
            assertTrue(target.stat(key).isEmpty());
        }
    }

    /** 导入包目录为符号链接时不得读取或写入链接目标。 */
    @Test
    public void shouldRejectSymbolicLinkImportPackage() throws Exception {
        org.junit.Assume.assumeTrue(!System.getProperty("os.name").toLowerCase()
                .contains("win"));
        Path root = temporaryFolder.getRoot().toPath();
        LocalFileStorageAdapter source = localStorage(root.resolve("source-link"));
        ObjectKey key = ObjectKey.of("reports/report.txt");
        put(source, key, "content");
        FileStorageMigrationService exporter = new FileStorageMigrationService(source, "documents",
                root.resolve("exports-link"), new ObjectMapper());
        Path packagePath = exporter.exportPackage("link-package");
        Path importRoot = root.resolve("imports-link");
        Files.createDirectories(importRoot);
        Files.createSymbolicLink(importRoot.resolve("link-package"), packagePath);
        LocalFileStorageAdapter target = localStorage(root.resolve("target-link"));
        FileStorageMigrationService importer = new FileStorageMigrationService(target, "documents",
                root.resolve("unused-link"), new ObjectMapper());

        try {
            importer.importPackage("link-package", importRoot, root.resolve("completed-link"),
                    root.resolve("failed-link"));
            fail("expected symbolic link package rejection");
        } catch (IllegalStateException expected) {
            assertTrue(target.stat(key).isEmpty());
            assertTrue(Files.isRegularFile(root.resolve("failed-link/link-package/errors.ndjson")));
        }
    }

    /** 使用临时目录创建本地存储。 */
    private LocalFileStorageAdapter localStorage(Path root) {
        return new LocalFileStorageAdapter(root, "test-signing-key".getBytes(StandardCharsets.UTF_8));
    }

    /** 写入测试对象。 */
    private void put(LocalFileStorageAdapter storage, ObjectKey key, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.put(new UploadRequest(key, new ByteArrayInputStream(bytes), bytes.length,
                "text/plain", CacheControl.privateNoCache()));
    }

    /** 篡改 manifest 第二行对应的对象文件。 */
    private void corruptSecondObject(Path packagePath) throws Exception {
        String[] lines = Files.readString(packagePath.resolve(FileStorageMigrationService.MANIFEST_NAME))
                .split(System.lineSeparator());
        com.fasterxml.jackson.databind.JsonNode entry = new ObjectMapper().readTree(lines[1]);
        Files.writeString(packagePath.resolve(entry.get("relativePath").asText()), "corrupt");
    }

    /** 将 manifest 第一项替换为不匹配对象 key 的相对路径。 */
    private void replaceRelativePath(Path packagePath, String relativePath) throws Exception {
        Path manifest = packagePath.resolve(FileStorageMigrationService.MANIFEST_NAME);
        com.fasterxml.jackson.databind.JsonNode entry = new ObjectMapper().readTree(Files.readString(manifest));
        Path original = packagePath.resolve(entry.get("relativePath").asText());
        Files.move(original, packagePath.resolve(relativePath));
        ((com.fasterxml.jackson.databind.node.ObjectNode) entry).put("relativePath", relativePath);
        Files.writeString(manifest, new ObjectMapper().writeValueAsString(entry) + System.lineSeparator());
    }

    /** 单对象迁移测试替身。 */
    private static final class SingleObjectStorage implements FileStorage {
        /** 对象元数据。 */
        private final ObjectMetadata metadata;
        /** 对象内容。 */
        private final byte[] content;

        /** 创建对象测试替身。 */
        private SingleObjectStorage(ObjectMetadata metadata, byte[] content) {
            this.metadata = metadata;
            this.content = content;
        }

        /** 枚举对象元数据。 */
        @Override public Stream<ObjectMetadata> list() { return metadata == null ? Stream.empty() : Stream.of(metadata); }
        /** 打开对象流。 */
        @Override public Optional<DownloadObject> get(ObjectKey key) {
            return metadata != null && metadata.key().equals(key)
                    ? Optional.of(new DownloadObject(new ByteArrayInputStream(content), metadata))
                    : Optional.empty();
        }
        /** 查询对象元数据。 */
        @Override public Optional<ObjectMetadata> stat(ObjectKey key) { return get(key).map(DownloadObject::metadata); }
        /** 不支持写入。 */
        @Override public ObjectMetadata put(UploadRequest request) { throw new UnsupportedOperationException(); }
        /** 判断对象存在。 */
        @Override public boolean exists(ObjectKey key) { return stat(key).isPresent(); }
        /** 不支持删除。 */
        @Override public boolean delete(ObjectKey key) { throw new UnsupportedOperationException(); }
        /** 不支持预签名上传。 */
        @Override public PresignedUpload presignUpload(PresignUploadRequest request) { throw new UnsupportedOperationException(); }
        /** 不支持预签名下载。 */
        @Override public PresignedDownload presignDownload(PresignDownloadRequest request) { throw new UnsupportedOperationException(); }
    }
}
