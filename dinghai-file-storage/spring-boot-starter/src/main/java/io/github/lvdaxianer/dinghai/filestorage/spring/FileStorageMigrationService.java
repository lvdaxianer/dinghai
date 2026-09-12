package io.github.lvdaxianer.dinghai.filestorage.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通过服务器目录完成文件存储包的流式导出。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class FileStorageMigrationService {
    /** 合法迁移包名称。 */
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,96}");
    /** manifest 文件名。 */
    static final String MANIFEST_NAME = "manifest.ndjson";
    /** 包对象目录。 */
    static final String OBJECTS_DIRECTORY = "objects";
    /** 当前 manifest 格式版本。 */
    private static final int MANIFEST_VERSION = 1;
    /** 安全错误清单文件名。 */
    private static final String ERRORS_NAME = "errors.ndjson";
    private final FileStorage storage;
    private final String storageAlias;
    private final Path exportRoot;
    private final ObjectMapper objectMapper;
    private final boolean atomicMovesEnabled;

    /**
     * 创建服务器目录导出服务。
     *
     * @param storage 当前文件存储 provider
     * @param storageAlias 逻辑存储别名
     * @param exportRoot 导出根目录
     * @param objectMapper manifest JSON 编解码器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public FileStorageMigrationService(FileStorage storage, String storageAlias, Path exportRoot,
                                       ObjectMapper objectMapper) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.storageAlias = Objects.requireNonNull(storageAlias, "storageAlias");
        this.exportRoot = Objects.requireNonNull(exportRoot, "exportRoot").toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        atomicMovesEnabled = true;
    }

    /**
     * 创建可模拟不支持原子目录移动的迁移服务。
     *
     * @param storage 当前文件存储 provider
     * @param storageAlias 逻辑存储别名
     * @param exportRoot 导出根目录
     * @param objectMapper manifest JSON 编解码器
     * @param atomicMovesEnabled 是否启用原子目录移动
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    FileStorageMigrationService(FileStorage storage, String storageAlias, Path exportRoot,
                                ObjectMapper objectMapper, boolean atomicMovesEnabled) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.storageAlias = Objects.requireNonNull(storageAlias, "storageAlias");
        this.exportRoot = Objects.requireNonNull(exportRoot, "exportRoot").toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.atomicMovesEnabled = atomicMovesEnabled;
    }

    /**
     * 将当前 provider 对象流式导出为原子发布的迁移包。
     *
     * @param packageName 严格校验后的迁移包名称
     * @return 已发布的迁移包目录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public Path exportPackage(String packageName) {
        validatePackageName(packageName);
        Path target = safeChild(exportRoot, packageName);
        Path temporary = safeChild(exportRoot, "." + packageName + ".tmp");
        try {
            Files.createDirectories(exportRoot);
            deleteRecursively(temporary);
            Files.createDirectories(temporary.resolve(OBJECTS_DIRECTORY));
            writeManifest(temporary);
            moveDirectory(temporary, target);
            return target;
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(temporary);
            throw new IllegalStateException("migration export failed", exception);
        }
    }

    /**
     * 导入指定目录中的迁移包，并移动到完成或失败目录。
     *
     * @param packageName 严格校验后的迁移包名称
     * @param importRoot 待导入包根目录
     * @param completedRoot 导入成功包根目录
     * @param failedRoot 导入失败包根目录
     * @return 成功后的终态包目录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    public Path importPackage(String packageName, Path importRoot, Path completedRoot,
                              Path failedRoot) {
        validatePackageName(packageName);
        Path normalizedImportRoot = importRoot.toAbsolutePath().normalize();
        Path source = safeChild(normalizedImportRoot, packageName);
        Path completed = safeChild(completedRoot.toAbsolutePath().normalize(), packageName);
        Path failed = safeChild(failedRoot.toAbsolutePath().normalize(), packageName);
        try {
            validateImportPackagePath(normalizedImportRoot, source);
            validateAndImport(source);
            Files.createDirectories(completed.getParent());
            moveDirectory(source, completed);
            return completed;
        } catch (MigrationFailure exception) {
            moveFailed(source, failed, exception);
            throw new IllegalStateException("migration import failed", exception);
        } catch (IOException | RuntimeException exception) {
            moveFailed(source, failed, new MigrationFailure("MIGRATION_IMPORT_FAILED",
                    "manifest", exception));
            throw new IllegalStateException("migration import failed", exception);
        }
    }

    /** 校验 manifest 并逐项流式写入当前 provider。 */
    private void validateAndImport(Path packageRoot) throws IOException {
        Path manifest = packageRoot.resolve(MANIFEST_NAME).normalize();
        if (!manifest.startsWith(packageRoot) || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("migration manifest is missing");
        }
        List<ValidatedEntry> entries = validateManifest(packageRoot, manifest);
        preflightTargetConflicts(entries);
        importEntries(entries);
    }

    /** 验证导入根、包目录及其内容均不通过符号链接逃逸。 */
    private void validateImportPackagePath(Path importRoot, Path packageRoot) throws IOException {
        if (Files.isSymbolicLink(importRoot) || Files.isSymbolicLink(packageRoot)
                || !Files.isDirectory(packageRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("migration package path is invalid");
        }
        try (var paths = Files.walk(packageRoot)) {
            if (paths.anyMatch(Files::isSymbolicLink)) {
                throw new IllegalArgumentException("migration package contains symbolic link");
            }
        }
    }

    /** 先验证全部 manifest 对象，避免后续错误造成部分导入。 */
    private List<ValidatedEntry> validateManifest(Path packageRoot, Path manifest) throws IOException {
        List<ValidatedEntry> entries = new ArrayList<>();
        Set<ObjectKey> keys = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.isBlank()) {
                    MigrationManifestEntry entry = readEntry(line, lineNumber);
                    validateUniqueKey(entry, keys, lineNumber);
                    entries.add(validateEntry(packageRoot, entry, lineNumber));
                }
            }
        }
        return entries;
    }

    /** 读取单行 manifest，并将解析错误标记为对应行号。 */
    private MigrationManifestEntry readEntry(String line, int lineNumber) throws MigrationFailure {
        try {
            return objectMapper.readValue(line, MigrationManifestEntry.class);
        } catch (IOException exception) {
            throw failure("MIGRATION_MANIFEST_ENTRY_INVALID", manifestSource(lineNumber), exception);
        }
    }

    /** 确保单个包中的每个对象 key 只出现一次。 */
    private void validateUniqueKey(MigrationManifestEntry entry, Set<ObjectKey> keys, int lineNumber)
            throws MigrationFailure {
        if (entry.key() == null || !keys.add(entry.key())) {
            throw failure("MIGRATION_OBJECT_KEY_INVALID", manifestSource(lineNumber), null);
        }
    }

    /** 校验单项路径、摘要和长度。 */
    private ValidatedEntry validateEntry(Path packageRoot, MigrationManifestEntry entry,
                                         int lineNumber) throws IOException {
        String source = manifestSource(lineNumber);
        if (entry.version() != MANIFEST_VERSION) {
            throw failure("MIGRATION_MANIFEST_VERSION_INVALID", source, null);
        }
        if (!storageAlias.equals(entry.storageAlias()) || entry.relativePath() == null
                || entry.contentLength() < 0 || entry.sha256() == null
                || entry.contentType() == null || entry.cacheControl() == null) {
            throw failure("MIGRATION_STORAGE_ALIAS_INVALID", source, null);
        }
        if (!canonicalRelativePath(entry.key()).equals(entry.relativePath())) {
            throw failure("MIGRATION_OBJECT_PATH_INVALID", source, null);
        }
        Path objectPath = packageRoot.resolve(entry.relativePath()).normalize();
        if (!objectPath.startsWith(packageRoot.resolve(OBJECTS_DIRECTORY))
                || !Files.isRegularFile(objectPath, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("MIGRATION_OBJECT_PATH_INVALID", source, null);
        }
        DigestResult digest = digestFile(objectPath);
        if (digest.length() != entry.contentLength() || !digest.sha256().equals(entry.sha256())) {
            throw failure("MIGRATION_OBJECT_DIGEST_MISMATCH", source, null);
        }
        return new ValidatedEntry(entry, objectPath, source);
    }

    /** 预检所有已存在目标，避免写入后才发现冲突。 */
    private void preflightTargetConflicts(List<ValidatedEntry> entries) throws IOException {
        for (ValidatedEntry entry : entries) {
            Optional<ObjectMetadata> existing = storage.stat(entry.entry().key());
            if (existing.isPresent()) {
                verifyExisting(entry.entry().key(), entry.entry().sha256(),
                        entry.entry().contentLength(), entry.source());
            }
        }
    }

    /** 将已完整校验且通过冲突预检的对象写入当前 provider。 */
    private void importEntries(List<ValidatedEntry> entries) throws IOException {
        for (ValidatedEntry entry : entries) {
            importMissingEntry(entry);
        }
    }

    /** 写入预检时不存在的单项对象。 */
    private void importMissingEntry(ValidatedEntry validated) throws IOException {
        MigrationManifestEntry entry = validated.entry();
        Optional<ObjectMetadata> existing = storage.stat(entry.key());
        if (existing.isPresent()) {
            verifyExisting(entry.key(), entry.sha256(), entry.contentLength(), validated.source());
            return;
        }
        try (InputStream input = Files.newInputStream(validated.objectPath())) {
            storage.put(new UploadRequest(entry.key(), input, entry.contentLength(),
                    entry.contentType(), entry.cacheControl()));
        }
    }

    /** 校验已有对象摘要，匹配时幂等跳过，不匹配时拒绝冲突。 */
    private void verifyExisting(ObjectKey key, String expectedSha256, long expectedLength,
                                String source) throws IOException {
        try (DownloadObject existing = storage.get(key).orElseThrow()) {
            DigestResult digest = copyAndDigest(existing.stream(), OutputStream.nullOutputStream());
            if (digest.length() != expectedLength || !digest.sha256().equals(expectedSha256)) {
                throw failure("MIGRATION_OBJECT_CONFLICT", source, null);
            }
        }
    }

    /** 计算文件 SHA-256 和长度。 */
    private DigestResult digestFile(Path objectPath) throws IOException {
        try (InputStream input = Files.newInputStream(objectPath)) {
            return copyAndDigest(input, OutputStream.nullOutputStream());
        }
    }

    /** 将失败包移动到 failed 目录并追加安全错误记录。 */
    private void moveFailed(Path source, Path failed, MigrationFailure failure) {
        try {
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Files.createDirectories(failed.getParent());
            if (Files.isSymbolicLink(source)) {
                relocateSymbolicLinkFailure(source, failed, failure);
                return;
            }
            moveDirectory(source, failed);
            Files.writeString(failed.resolve(ERRORS_NAME), errorRecord(failure),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("migration failed package relocation failed", exception);
        }
    }

    /** 将非法符号链接记录为安全失败目录，不跟随其指向的外部路径。 */
    private void relocateSymbolicLinkFailure(Path source, Path failed, MigrationFailure failure)
            throws IOException {
        Files.createDirectory(failed);
        Files.deleteIfExists(source);
        Files.writeString(failed.resolve(ERRORS_NAME), errorRecord(failure),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** 流式写出对象文件和 NDJSON manifest。 */
    private void writeManifest(Path packageRoot) throws IOException {
        try (BufferedWriter manifest = Files.newBufferedWriter(packageRoot.resolve(MANIFEST_NAME),
                StandardCharsets.UTF_8)) {
            try (var objects = storage.list()) {
                objects.forEach(metadata -> writeEntry(packageRoot, manifest, metadata));
            }
        }
    }

    /** 写出一项对象内容和对应 manifest 记录。 */
    private void writeEntry(Path packageRoot, BufferedWriter manifest, ObjectMetadata metadata) {
        String relativePath = canonicalRelativePath(metadata.key());
        Path objectPath = packageRoot.resolve(relativePath);
        try (DownloadObject download = storage.get(metadata.key()).orElseThrow();
             InputStream input = download.stream();
             OutputStream output = Files.newOutputStream(objectPath)) {
            DigestResult result = copyAndDigest(input, output);
            if (result.length() != metadata.contentLength()) {
                throw new IllegalStateException("migration source length mismatch");
            }
            MigrationManifestEntry entry = new MigrationManifestEntry(MANIFEST_VERSION, storageAlias,
                    metadata.key(),
                    relativePath, result.length(), metadata.contentType(), metadata.cacheControl(),
                    result.sha256());
            manifest.write(objectMapper.writeValueAsString(entry));
            manifest.newLine();
        } catch (IOException exception) {
            throw new IllegalStateException("migration object export failed", exception);
        }
    }

    /** 流式复制并计算 SHA-256。 */
    private DigestResult copyAndDigest(InputStream input, OutputStream output) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long length = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                length += read;
            }
            return new DigestResult(length, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 生成包内稳定且不暴露对象 key 的文件名摘要。 */
    private String digest(ObjectKey key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 返回与对象 key 一一对应的规范包内相对路径。 */
    private String canonicalRelativePath(ObjectKey key) {
        return OBJECTS_DIRECTORY + "/" + digest(key) + ".bin";
    }

    /** 校验并解析包名。 */
    static void validatePackageName(String packageName) {
        if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches()
                || packageName.startsWith(".")) {
            throw new IllegalArgumentException("migration package name is invalid");
        }
    }

    /** 将子路径封闭在配置根目录内。 */
    private Path safeChild(Path root, String child) {
        Path candidate = root.resolve(child).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("migration path is invalid");
        }
        return candidate;
    }

    /** 优先原子移动，目标文件系统不支持时使用受控递归移动。 */
    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            moveAtomically(source, target);
        } catch (AtomicMoveNotSupportedException exception) {
            moveRecursively(source, target);
        }
    }

    /** 使用文件系统原子移动发布目录。 */
    private void moveAtomically(Path source, Path target) throws IOException {
        if (!atomicMovesEnabled) {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(),
                    "disabled for test");
        }
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /** 在目标目录不存在时递归复制，全部成功后再删除源目录。 */
    private void moveRecursively(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            throw new IOException("migration target package already exists");
        }
        try {
            Files.createDirectories(target);
            try (var paths = Files.walk(source)) {
                paths.forEach(path -> copyPath(source, target, path));
            }
            deleteRecursively(source);
        } catch (RuntimeException exception) {
            deleteRecursively(target);
            throw exception;
        } catch (IOException exception) {
            deleteRecursively(target);
            throw exception;
        }
    }

    /** 将源包中的单个目录项复制到目标目录。 */
    private void copyPath(Path source, Path target, Path path) {
        try {
            Path relativePath = source.relativize(path);
            Path destination = target.resolve(relativePath);
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination);
            } else {
                Files.copy(path, destination);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("migration directory copy failed", exception);
        }
    }

    /** 构造可供运维定位且不暴露对象内容的错误记录。 */
    private String errorRecord(MigrationFailure failure) {
        try {
            return objectMapper.writeValueAsString(new MigrationErrorEntry(failure.code(),
                    failure.source())) + System.lineSeparator();
        } catch (IOException exception) {
            throw new IllegalStateException("migration error serialization failed", exception);
        }
    }

    /** 创建带稳定错误码和来源的导入失败。 */
    private MigrationFailure failure(String code, String source, Throwable cause) {
        return new MigrationFailure(code, source, cause);
    }

    /** 返回错误清单中使用的稳定 manifest 行号来源。 */
    private String manifestSource(int lineNumber) {
        return "manifest:" + lineNumber;
    }

    /** 删除临时导出目录。 */
    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new IllegalStateException(exception); }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("migration temporary cleanup failed", exception);
        }
    }

    /** 流式摘要结果。 */
    private record DigestResult(long length, String sha256) {
    }

    /** 已验证、可安全写入 provider 的迁移项。 */
    private record ValidatedEntry(MigrationManifestEntry entry, Path objectPath, String source) {
    }

    /** manifest 导入时可恢复的结构化失败。 */
    private static final class MigrationFailure extends IOException {
        /** 稳定错误码。 */
        private final String code;
        /** manifest 行号或通用来源。 */
        private final String source;

        /** 创建结构化迁移失败。 */
        private MigrationFailure(String code, String source, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.source = source;
        }

        /** 返回稳定错误码。 */
        private String code() {
            return code;
        }

        /** 返回安全来源。 */
        private String source() {
            return source;
        }
    }

    /** 写入 errors.ndjson 的安全错误模型。 */
    private record MigrationErrorEntry(String code, String source) {
    }
}
