package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.LocalFileSource;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** 基于本地磁盘的流式文件存储适配器。 */
public final class LocalFileStorageAdapter implements FileStorage, LocalFileSource,
        MultipartFileStorage {
    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final Logger LOGGER = Logger.getLogger(LocalFileStorageAdapter.class.getName());
    /** 本地预签名 URL 的默认最长有效期。 */
    public static final Duration DEFAULT_MAXIMUM_PRESIGN_EXPIRY = Duration.ofHours(1);
    private static final String DEFAULT_PATH_PREFIX = "/files";
    private final LocalObjectPathResolver paths;
    private final LocalMetadataStore metadataStore;
    private final LocalFileStorageSigner signer;
    private final long maxSizeBytes;
    private final Set<String> contentTypeWhitelist;
    private final String pathPrefix;
    private final String uploadBasePath;
    private final String downloadBasePath;
    private final String previewBasePath;
    private final Path multipartRoot;
    private final Clock clock;
    private final boolean cleanupEmptyDirectories;

    /** 使用默认 100 MB 上限创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey) {
        this(root, signingKey, UploadRequest.DEFAULT_MAX_SIZE_BYTES);
    }

    /** 使用默认上传上限和指定目录清理策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey,
                                   boolean cleanupEmptyDirectories) {
        this(root, signingKey, UploadRequest.DEFAULT_MAX_SIZE_BYTES, cleanupEmptyDirectories);
    }

    /** 使用指定上传上限创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes) {
        this(root, signingKey, maxSizeBytes, Set.of(), DEFAULT_MAXIMUM_PRESIGN_EXPIRY,
                Clock.systemUTC(), true);
    }

    /** 使用指定上传上限和目录清理策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   boolean cleanupEmptyDirectories) {
        this(root, signingKey, maxSizeBytes, Set.of(), DEFAULT_MAXIMUM_PRESIGN_EXPIRY,
                Clock.systemUTC(), cleanupEmptyDirectories);
    }

    /** 使用指定上传和签名策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                DEFAULT_PATH_PREFIX, true);
    }

    /** 使用指定上传和签名策略及目录清理策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock,
                                   boolean cleanupEmptyDirectories) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                DEFAULT_PATH_PREFIX, cleanupEmptyDirectories);
    }

    /** 使用指定 Web 路径前缀创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                pathPrefix, null, true);
    }

    /** 使用指定 Web 路径和目录清理策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix,
                                   boolean cleanupEmptyDirectories) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                pathPrefix, null, cleanupEmptyDirectories);
    }

    /**
     * 使用指定 multipart 临时目录创建本地适配器。
     *
     * @param root 正式对象根目录
     * @param signingKey 本地预签名密钥
     * @param maxSizeBytes 对象最大字节数
     * @param contentTypeWhitelist 允许的内容类型
     * @param maximumPresignExpiry 预签名最大有效期
     * @param clock 用于会话过期判断的时钟
     * @param pathPrefix 本地 Web 访问路径前缀
     * @param multipartRoot 独立的分片临时根目录
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix,
                                   Path multipartRoot) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                pathPrefix, multipartRoot, operationBasePath(pathPrefix, "upload"),
                operationBasePath(pathPrefix, "download"), operationBasePath(pathPrefix, "preview"),
                true);
    }

    /** 使用指定 multipart 临时目录和目录清理策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix,
                                   Path multipartRoot, boolean cleanupEmptyDirectories) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                pathPrefix, multipartRoot, operationBasePath(pathPrefix, "upload"),
                operationBasePath(pathPrefix, "download"), operationBasePath(pathPrefix, "preview"),
                cleanupEmptyDirectories);
    }

    /**
     * 使用指定操作 URL 基础路径创建本地适配器。
     *
     * @param root 正式对象根目录
     * @param signingKey 本地预签名密钥
     * @param maxSizeBytes 对象最大字节数
     * @param contentTypeWhitelist 允许的内容类型
     * @param maximumPresignExpiry 预签名最大有效期
     * @param clock 用于会话过期判断的时钟
     * @param pathPrefix 本地 multipart Web 访问路径前缀
     * @param multipartRoot 独立的分片临时根目录
     * @param uploadBasePath 上传 URL 的无对象 key 基础路径
     * @param downloadBasePath 下载 URL 的无对象 key 基础路径
     * @param previewBasePath 预览 URL 的无对象 key 基础路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix,
                                   Path multipartRoot, String uploadBasePath, String downloadBasePath,
                                   String previewBasePath) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                pathPrefix, multipartRoot, uploadBasePath, downloadBasePath, previewBasePath, true);
    }

    /** 使用完整本地策略创建适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix,
                                   Path multipartRoot, String uploadBasePath, String downloadBasePath,
                                   String previewBasePath, boolean cleanupEmptyDirectories) {
        if (maxSizeBytes < 0) {
            throw new IllegalArgumentException("max size must be non-negative");
        }
        paths = new LocalObjectPathResolver(root);
        metadataStore = new LocalMetadataStore(paths);
        signer = new LocalFileStorageSigner(signingKey, maximumPresignExpiry, clock);
        this.maxSizeBytes = maxSizeBytes;
        this.contentTypeWhitelist = Set.copyOf(Objects.requireNonNull(
                contentTypeWhitelist, "contentTypeWhitelist"));
        this.pathPrefix = LocalFileStorageSigner.validatePathPrefix(pathPrefix);
        this.uploadBasePath = LocalFileStorageSigner.validatePathPrefix(uploadBasePath);
        this.downloadBasePath = LocalFileStorageSigner.validatePathPrefix(downloadBasePath);
        this.previewBasePath = LocalFileStorageSigner.validatePathPrefix(previewBasePath);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cleanupEmptyDirectories = cleanupEmptyDirectories;
        Path configuredMultipartRoot = multipartRoot == null
                ? paths.root().resolveSibling(paths.root().getFileName() + ".dinghai-multipart")
                : multipartRoot.toAbsolutePath().normalize();
        configuredMultipartRoot = resolveExistingPath(configuredMultipartRoot);
        // 临时会话清理不能递归删除正式对象目录。
        if (configuredMultipartRoot.startsWith(paths.root())
                || paths.root().startsWith(configuredMultipartRoot)) {
            throw new IllegalArgumentException("multipart root overlaps local storage root");
        }
        this.multipartRoot = configuredMultipartRoot;
        createDirectory(this.multipartRoot);
    }

    /** 写入或覆盖对象，并以临时文件和原子移动完成替换。 */
    @Override
    public ObjectMetadata put(UploadRequest request) {
        Objects.requireNonNull(request, "request");
        Path target = paths.resolve(request.key());
        validateRequestOrClose(request);
        try {
            try (InputStream content = request.content()) {
                Files.createDirectories(target.getParent());
                return writeObject(request, target, content);
            }
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object write failed", exception);
        }
    }

    /** 获取对象流，不存在时返回空。 */
    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        Path path = paths.resolve(key);
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            Optional<ObjectMetadata> metadata = stat(key);
            if (metadata.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DownloadObject(Files.newInputStream(path), metadata.get()));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object read failed", exception);
        }
    }

    /** 返回已完成且仍在本地存储根目录内的普通对象文件。 */
    @Override
    public Optional<Path> localFile(ObjectKey key) {
        Path path = paths.resolve(key);
        return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(path) : Optional.empty();
    }

    /** 获取对象元数据，不存在时返回空。 */
    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        Path path = paths.resolve(key);
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readMetadata(key, path));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object metadata read failed", exception);
        }
    }

    /** 判断对象是否存在且为普通文件。 */
    @Override
    public boolean exists(ObjectKey key) {
        return Files.isRegularFile(paths.resolve(key), java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    /** 删除对象和元数据；对象不存在时返回 false。 */
    @Override
    public boolean delete(ObjectKey key) {
        Path path = paths.resolve(key);
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            deleteMissingObjectMetadata(key);
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(path);
            metadataStore.delete(key);
            if (deleted && cleanupEmptyDirectories) {
                cleanupParentDirectories(path.getParent(), new DeleteSummary());
            }
            return deleted;
        } catch (NoSuchFileException exception) {
            deleteMissingObjectMetadata(key);
            return false;
        } catch (IOException exception) {
            logObjectDeleteFailure(key, exception);
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object delete failed", exception);
        }
    }

    /** 删除对象前残留的元数据，并保持对象不存在时的幂等语义。 */
    private void deleteMissingObjectMetadata(ObjectKey key) {
        try {
            metadataStore.delete(key);
        } catch (IOException exception) {
            logObjectDeleteFailure(key, exception);
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object metadata delete failed", exception);
        }
    }

    /**
     * 删除本地前缀下的对象，并在开关开启时回收空目录。
     *
     * @param rawPrefix 原始对象前缀，可包含 URL 百分号编码
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    public void deletePrefix(String rawPrefix) {
        ObjectKey prefix = paths.normalizePrefix(rawPrefix);
        Path prefixPath = paths.resolvePrefix(prefix);
        DeleteSummary summary = new DeleteSummary();
        long startedAt = System.nanoTime();
        try {
            deletePrefixObjects(prefix, prefixPath, summary);
            if (cleanupEmptyDirectories) {
                cleanupPrefixDirectories(prefixPath, summary);
            }
        } finally {
            logPrefixDeleteSummary(prefix, summary, elapsedMillis(startedAt));
        }
        if (summary.failedDeletions > 0) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local prefix delete was incomplete", null);
        }
    }

    /** 扫描前缀范围并逐个删除普通对象及其元数据。 */
    private void deletePrefixObjects(ObjectKey prefix, Path prefixPath, DeleteSummary summary) {
        try (Stream<Path> objects = Files.walk(prefixPath)) {
            objects.filter(this::isRegularObjectFile)
                    .map(paths.root()::relativize)
                    .map(this::objectKey)
                    .filter(key -> matchesPrefix(key, prefix))
                    .forEach(key -> deletePrefixObject(key, summary));
        } catch (NoSuchFileException exception) {
            // 并发任务已经删除前缀目录，按幂等成功处理。
        } catch (IOException | UncheckedIOException exception) {
            summary.failedDeletions++;
            logPrefixDeleteFailure(prefix, exception);
        }
    }

    /** 删除前缀范围内的一个对象，失败时继续处理其余对象。 */
    private void deletePrefixObject(ObjectKey key, DeleteSummary summary) {
        try {
            Path path = paths.resolve(key);
            if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                deleteMissingObjectMetadata(key);
                return;
            }
            if (Files.deleteIfExists(path)) {
                summary.deletedFiles++;
            }
            metadataStore.delete(key);
        } catch (NoSuchFileException exception) {
            deleteMissingObjectMetadata(key);
        } catch (IOException | FileStorageException exception) {
            summary.failedDeletions++;
            logObjectDeleteFailure(key, exception);
        }
    }

    /** 清理前缀范围内的目录并继续向上清理其空父目录。 */
    private void cleanupPrefixDirectories(Path prefixPath, DeleteSummary summary) {
        if (!Files.isDirectory(prefixPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            cleanupParentDirectories(prefixPath.getParent(), summary);
            return;
        }
        Path start = prefixPath;
        if (!start.startsWith(paths.root())) {
            return;
        }
        try (Stream<Path> directories = Files.walk(start)) {
            directories.filter(this::isDirectoryCandidate)
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(directory -> deleteEmptyDirectory(directory, summary));
        } catch (NoSuchFileException exception) {
            // 并发任务已经删除目录，按幂等成功处理。
        } catch (IOException | UncheckedIOException exception) {
            summary.failedDeletions++;
            logPrefixDeleteFailure(null, exception);
        }
        cleanupParentDirectories(start.getParent(), summary);
    }

    /** 从对象父目录开始逐层回收空目录，遇到不能删除的目录即停止。 */
    private void cleanupParentDirectories(Path start, DeleteSummary summary) {
        Path current = start;
        while (current != null && current.startsWith(paths.root())
                && !current.equals(paths.root())) {
            DeleteDirectoryResult result = deleteEmptyDirectory(current, summary);
            if (result != DeleteDirectoryResult.DELETED
                    && result != DeleteDirectoryResult.ABSENT) {
                return;
            }
            current = current.getParent();
        }
    }

    /** 尝试删除一个空目录并返回可用于并发控制的结果。 */
    private DeleteDirectoryResult deleteEmptyDirectory(Path directory, DeleteSummary summary) {
        if (!directory.startsWith(paths.root()) || directory.equals(paths.root())
                || Files.isSymbolicLink(directory)) {
            summary.skippedDirectories++;
            return DeleteDirectoryResult.SKIPPED;
        }
        if (!Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return DeleteDirectoryResult.ABSENT;
        }
        try {
            if (Files.deleteIfExists(directory)) {
                summary.deletedDirectories++;
                return DeleteDirectoryResult.DELETED;
            }
            return DeleteDirectoryResult.ABSENT;
        } catch (DirectoryNotEmptyException exception) {
            summary.skippedDirectories++;
            return DeleteDirectoryResult.NON_EMPTY;
        } catch (NoSuchFileException exception) {
            return DeleteDirectoryResult.ABSENT;
        } catch (IOException exception) {
            summary.failedDeletions++;
            summary.skippedDirectories++;
            logDirectoryDeleteFailure(directory, exception);
            return DeleteDirectoryResult.FAILED;
        }
    }

    /** 判断目录遍历结果是否为可清理目录或需统计的符号链接。 */
    private boolean isDirectoryCandidate(Path path) {
        return !path.equals(paths.root()) && (Files.isDirectory(path,
                java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path));
    }

    /** 判断对象 key 是否等于前缀或位于前缀目录下。 */
    private static boolean matchesPrefix(ObjectKey key, ObjectKey prefix) {
        return key.value().equals(prefix.value())
                || key.value().startsWith(prefix.value() + "/");
    }

    /** 将 root 下的相对路径转换为规范化对象 key。 */
    private ObjectKey objectKey(Path relative) {
        String value = relative.toString().replace(java.io.File.separatorChar, '/');
        return ObjectKey.of(value);
    }

    /** 输出对象删除失败的安全告警。 */
    private static void logObjectDeleteFailure(ObjectKey key, Exception exception) {
        LOGGER.log(Level.WARNING, "[本地对象删除] 删除失败, keyDigest={0}, reason={1}",
                new Object[]{digestForLog(key.value()), safeFailureReason(exception)});
    }

    /** 输出目录删除失败的安全告警。 */
    private static void logDirectoryDeleteFailure(Path directory, Exception exception) {
        LOGGER.log(Level.WARNING, "[本地目录回收] 删除失败, directoryName={0}, reason={1}",
                new Object[]{directory.getFileName(), safeFailureReason(exception)});
    }

    /** 输出前缀删除失败的安全告警。 */
    private static void logPrefixDeleteFailure(ObjectKey prefix, Exception exception) {
        LOGGER.log(Level.WARNING, "[本地前缀删除] 扫描失败, prefix={0}, reason={1}",
                new Object[]{prefix == null ? "[REDACTED]" : digestForLog(prefix.value()),
                        safeFailureReason(exception)});
    }

    /** 输出前缀删除的结构化汇总日志。 */
    private static void logPrefixDeleteSummary(ObjectKey prefix, DeleteSummary summary,
                                               long durationMs) {
        LOGGER.log(Level.INFO, "[本地前缀删除] 完成, prefix={0}, deletedFiles={1}, "
                        + "deletedDirectories={2}, skippedDirectories={3}, failedDeletions={4}, "
                        + "durationMs={5}",
                new Object[]{digestForLog(prefix.value()), summary.deletedFiles,
                        summary.deletedDirectories, summary.skippedDirectories,
                        summary.failedDeletions, durationMs});
    }

    /** 计算不包含原始对象 key 的安全标识。 */
    private static String digestForLog(String value) {
        return java.util.HexFormat.of().formatHex(sha256()
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** 将删除耗时转换为非负毫秒数。 */
    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    /** 将异常转换为限长且不带路径内容的安全原因。 */
    private static String safeFailureReason(Exception exception) {
        return exception.getClass().getSimpleName();
    }

    /** 惰性枚举根目录中的普通对象，关闭流时释放目录遍历句柄。 */
    @Override
    public Stream<ObjectMetadata> list() {
        try {
            Path root = paths.root();
            Stream<Path> walk = Files.walk(root);
            return walk.filter(this::isRegularObjectFile).map(root::relativize)
                    .map(Path::toString).map(value -> value.replace(java.io.File.separatorChar, '/'))
                    .map(ObjectKey::of).map(this::readMetadataForListing).flatMap(Optional::stream)
                    .onClose(walk::close);
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object listing failed", exception);
        }
    }

    /** 读取列表对象元数据；单个对象异常时记录告警并跳过。 */
    private Optional<ObjectMetadata> readMetadataForListing(ObjectKey key) {
        Path metadata = paths.metadata(key);
        if (!Files.isRegularFile(metadata, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            logMetadataListingSkip(key, "metadata file is missing");
            return Optional.empty();
        }
        try {
            Path object = paths.resolve(key);
            return Optional.of(readMetadata(key, object));
        } catch (IOException | FileStorageException | IllegalArgumentException exception) {
            logMetadataListingSkip(key, safeMetadataFailureReason(exception));
            return Optional.empty();
        }
    }

    /** 记录安全且限长的单对象元数据跳过告警。 */
    private static void logMetadataListingSkip(ObjectKey key, String reason) {
        LOGGER.log(Level.WARNING, "[本地文件枚举] 跳过元数据异常对象, objectPath={0}, reason={1}",
                new Object[]{key.value(), reason});
    }

    /** 将元数据读取异常转换为不含完整路径或文件内容的安全原因。 */
    private static String safeMetadataFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String safeMessage = message.replace('\r', ' ').replace('\n', ' ');
        if (safeMessage.length() > 128) {
            safeMessage = safeMessage.substring(0, 128);
        }
        return exception.getClass().getSimpleName() + ": " + safeMessage;
    }

    /** 判断目录项是否为不跟随符号链接的普通对象文件。 */
    private boolean isRegularObjectFile(Path path) {
        return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    /** 生成本地预签名上传地址。 */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        validatePresignUpload(request);
        return signer.presignUploadAt(request, uploadBasePath);
    }

    /** 生成本地预签名下载地址。 */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return signer.presignDownloadAt(request, downloadBasePath, previewBasePath);
    }

    /** 验证本地预签名上传请求。 */
    public void verifyPresignedUpload(PresignUploadRequest request, Instant expiresAt,
                                      String signature, Instant now) {
        signer.verifyUpload(request, expiresAt, signature, now);
    }

    /** 验证本地预签名下载或预览请求。 */
    public void verifyPresignedDownload(PresignDownloadRequest request, Instant expiresAt,
                                        String signature, Instant now) {
        signer.verifyDownload(request, expiresAt, signature, now);
    }

    /** 创建本地临时分片会话并持久化其元数据。 */
    @Override
    public MultipartUploadSession initiate(MultipartUploadRequest request) {
        Objects.requireNonNull(request, "request");
        String uploadId = UUID.randomUUID().toString();
        MultipartUploadSession session = new MultipartUploadSession(uploadId, request,
                clock.instant().plus(request.expiresIn()));
        Path directory = multipartDirectory(uploadId);
        try {
            Files.createDirectories(directory.resolve("parts"));
            writeSession(session, directory, "OPEN", null);
            return session;
        } catch (IOException exception) {
            deleteTemporaryDirectory(directory);
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "multipart session creation failed", exception);
        }
    }

    /** 生成绑定本地分片会话的上传地址。 */
    @Override
    public PresignedUpload presignPart(MultipartUploadSession expected, MultipartPartRequest request) {
        SessionData session = requireOpenOrCompleted(expected);
        if (session.completed()) {
            throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart session is already completed", null);
        }
        validatePartNumber(request.partNumber(), session.session());
        Instant expiresAt = session.session().expiresAt();
        if (!expiresAt.isAfter(clock.instant())) {
            throw failure(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired", null);
        }
        String url = ("/".equals(pathPrefix) ? "" : pathPrefix) + "/multipart/" + request.uploadId()
                + "/parts/" + request.partNumber();
        return new PresignedUpload(url, "PUT", expiresAt,
                Map.of("Content-Type", session.session().contentType()), Map.of());
    }

    /** 组合默认操作 URL 基础路径。 */
    private static String operationBasePath(String pathPrefix, String operation) {
        String prefix = LocalFileStorageSigner.validatePathPrefix(pathPrefix);
        return ("/".equals(prefix) ? "" : prefix) + "/" + operation;
    }

    /** 将一个分片先写临时文件，再原子替换为已确认分片。 */
    @Override
    public MultipartPart uploadPart(MultipartUploadSession expected, MultipartPartRequest request, InputStream content,
                                    long contentLength) {
        Objects.requireNonNull(content, "content");
        SessionData session = requireOpen(expected);
        validatePartNumber(request.partNumber(), session.session());
        long expectedLength = expectedPartSize(session.session(), request.partNumber());
        if (contentLength != expectedLength) {
            closeQuietly(content);
            throw failure(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart part length is invalid", null);
        }
        Path part = multipartDirectory(request.uploadId()).resolve("parts")
                .resolve(Integer.toString(request.partNumber()));
        try {
            Files.createDirectories(part.getParent());
            Path temporary = Files.createTempFile(part.getParent(), ".part-", ".tmp");
            try {
                WriteResult result = streamPart(content, contentLength, temporary);
                atomicMove(temporary, part);
                return new MultipartPart(request.partNumber(), result.length(), result.etag());
            } finally {
                deleteTemporary(temporary);
            }
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "multipart part write failed", exception);
        } finally {
            closeQuietly(content);
        }
    }

    /** 从本地临时目录读取已确认分片清单。 */
    @Override
    public List<MultipartPart> listParts(MultipartUploadSession expected) {
        SessionData session = requireSession(expected);
        String uploadId = expected.uploadId();
        if (session.completed()) {
            return session.parts();
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(
                multipartDirectory(uploadId).resolve("parts"))) {
            List<MultipartPart> parts = new ArrayList<>();
            for (Path path : files) {
                if (Files.isRegularFile(path)) {
                    parts.add(readPart(path, session.session()));
                }
            }
            return parts.stream().sorted(Comparator.comparingInt(MultipartPart::partNumber))
                    .toList();
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "multipart part listing failed", exception);
        }
    }

    /** 校验清单后流式合并分片，并原子发布最终对象。 */
    @Override
    public synchronized ObjectMetadata complete(MultipartUploadSession expected, MultipartCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        SessionData session = requireSession(expected);
        String uploadId = expected.uploadId();
        if (session.completed()) {
            return stat(session.session().key()).orElseThrow();
        }
        if (!session.session().expiresAt().isAfter(clock.instant())) {
            abort(expected);
            throw failure(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired", null);
        }
        List<MultipartPart> parts = validateCompletion(session, completion.parts());
        Path target = paths.resolve(session.session().key());
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".multipart-", ".tmp");
            try {
                WriteResult result = mergeParts(uploadId, parts, temporary);
                atomicMove(temporary, target);
                ObjectMetadata metadata = new ObjectMetadata(session.session().key(),
                        result.length(), session.session().contentType(), result.etag(),
                        clock.instant(), session.session().cacheControl());
                writeMetadata(metadata);
                writeSession(session.session().withParts(parts), multipartDirectory(uploadId),
                        "COMPLETED", metadata);
                return metadata;
            } finally {
                deleteTemporary(temporary);
            }
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "multipart completion failed", exception);
        }
    }

    /** 删除本地临时会话，重复取消保持幂等。 */
    @Override
    public synchronized void abort(MultipartUploadSession session) {
        if (session == null || session.uploadId() == null || session.uploadId().isBlank()) {
            return;
        }
        deleteTemporaryDirectory(multipartDirectory(session.uploadId()));
    }

    /** 删除指定时间前已经过期的本地临时会话。 */
    public int cleanupExpiredMultipartSessions(Instant now) {
        Objects.requireNonNull(now, "now");
        try (Stream<Path> sessions = Files.list(multipartRoot)) {
            return sessions.filter(Files::isDirectory).mapToInt(path -> cleanupIfExpired(path, now))
                    .sum();
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "multipart cleanup failed", exception);
        }
    }

    private SessionData requireOpen(MultipartUploadSession expected) {
        SessionData data = requireSession(expected);
        if (data.completed()) {
            throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart session is already completed", null);
        }
        if (!data.session().expiresAt().isAfter(clock.instant())) {
            abort(expected);
            throw failure(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired", null);
        }
        return data;
    }

    private SessionData requireOpenOrCompleted(MultipartUploadSession expected) {
        SessionData data = requireSession(expected);
        if (!data.session().expiresAt().isAfter(clock.instant()) && !data.completed()) {
            abort(expected);
            throw failure(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired", null);
        }
        return data;
    }

    private SessionData requireSession(String uploadId) {
        Path directory = multipartDirectory(uploadId);
        Path metadata = directory.resolve("session.properties");
        if (!Files.isRegularFile(metadata)) {
            throw failure(FileStorageErrorCode.MULTIPART_NOT_FOUND,
                    "multipart session was not found", null);
        }
        try (InputStream input = Files.newInputStream(metadata)) {
            Properties values = new Properties();
            values.load(input);
            MultipartUploadSession session = readSession(uploadId, values);
            String status = values.getProperty("status", "OPEN");
            List<MultipartPart> parts = readParts(values);
            return new SessionData(session.withParts(parts), "COMPLETED".equals(status), parts);
        } catch (IOException | RuntimeException exception) {
            throw failure(FileStorageErrorCode.MULTIPART_NOT_FOUND,
                    "multipart session metadata is invalid", exception);
        }
    }

    private SessionData requireSession(MultipartUploadSession expected) {
        Objects.requireNonNull(expected, "session");
        SessionData actual = requireSession(expected.uploadId());
        if (!actual.session().key().equals(expected.key())
                || actual.session().partCount() != expected.partCount()
                || actual.session().totalSize() != expected.totalSize()) {
            throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart session does not match stored state", null);
        }
        return actual;
    }

    private MultipartUploadSession readSession(String uploadId, Properties values) {
        ObjectKey key = ObjectKey.of(values.getProperty("key"));
        long totalSize = Long.parseLong(values.getProperty("totalSize"));
        long partSize = Long.parseLong(values.getProperty("partSize"));
        Instant expiresAt = Instant.parse(values.getProperty("expiresAt"));
        MultipartUploadRequest request = new MultipartUploadRequest(key, totalSize, partSize,
                values.getProperty("contentType"), new CacheControl(values.getProperty("cacheControl")),
                Duration.ofNanos(1));
        return new MultipartUploadSession(uploadId, key, totalSize, partSize,
                request.contentType(), request.cacheControl(), Integer.parseInt(values.getProperty("partCount")),
                expiresAt, List.of());
    }

    private List<MultipartPart> readParts(Properties values) {
        int count = Integer.parseInt(values.getProperty("part.entries", "0"));
        List<MultipartPart> parts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int number = Integer.parseInt(values.getProperty("part." + index + ".number"));
            long size = Long.parseLong(values.getProperty("part." + index + ".size"));
            String etag = values.getProperty("part." + index + ".etag");
            parts.add(new MultipartPart(number, size, etag));
        }
        return parts;
    }

    private void writeSession(MultipartUploadSession session, Path directory, String status,
                              ObjectMetadata metadata) throws IOException {
        Properties values = new Properties();
        values.setProperty("status", status);
        values.setProperty("key", session.key().value());
        values.setProperty("totalSize", Long.toString(session.totalSize()));
        values.setProperty("partSize", Long.toString(session.partSize()));
        values.setProperty("partCount", Integer.toString(session.partCount()));
        values.setProperty("contentType", session.contentType());
        values.setProperty("cacheControl", session.cacheControl().value());
        values.setProperty("expiresAt", session.expiresAt().toString());
        List<MultipartPart> parts = metadata == null ? listPartsIfPresent(directory) : session.parts();
        values.setProperty("part.entries", Integer.toString(parts.size()));
        for (int index = 0; index < parts.size(); index++) {
            MultipartPart part = parts.get(index);
            values.setProperty("part." + index + ".number", Integer.toString(part.partNumber()));
            values.setProperty("part." + index + ".size", Long.toString(part.size()));
            values.setProperty("part." + index + ".etag", part.etag());
        }
        Path target = directory.resolve("session.properties");
        Path temporary = Files.createTempFile(directory, ".session-", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, null);
            atomicMove(temporary, target);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private List<MultipartPart> listPartsIfPresent(Path directory) throws IOException {
        Path partsDirectory = directory.resolve("parts");
        if (!Files.isDirectory(partsDirectory)) {
            return List.of();
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(partsDirectory)) {
            List<MultipartPart> parts = new ArrayList<>();
            for (Path path : files) {
                if (Files.isRegularFile(path)) {
                    parts.add(readPart(path, null));
                }
            }
            return parts.stream().sorted(Comparator.comparingInt(MultipartPart::partNumber)).toList();
        }
    }

    private MultipartPart readPart(Path path, MultipartUploadSession session) throws IOException {
        int partNumber = Integer.parseInt(path.getFileName().toString());
        if (session != null) {
            validatePartNumber(partNumber, session);
        }
        long size = Files.size(path);
        return new MultipartPart(partNumber, size, calculateEtag(path));
    }

    private List<MultipartPart> validateCompletion(SessionData session, List<MultipartPart> requested) {
        List<MultipartPart> parts = requested.stream().sorted(Comparator.comparingInt(MultipartPart::partNumber)).toList();
        if (parts.size() != session.session().partCount()) {
            throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart completion parts are incomplete", null);
        }
        for (int index = 0; index < parts.size(); index++) {
            MultipartPart expected = parts.get(index);
            if (expected.partNumber() != index + 1) {
                throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                        "multipart completion parts are not contiguous", null);
            }
            MultipartPart actual = findPart(session, expected.partNumber());
            if (actual.size() != expected.size() || !actual.etag().equals(expected.etag())) {
                throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                        "multipart completion part does not match", null);
            }
        }
        return parts;
    }

    private MultipartPart findPart(SessionData session, int partNumber) {
        return listParts(session.session()).stream()
                .filter(part -> part.partNumber() == partNumber).findFirst()
                .orElseThrow(() -> failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                        "multipart completion part is missing", null));
    }

    private WriteResult mergeParts(String uploadId, List<MultipartPart> parts, Path temporary)
            throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        try (OutputStream output = Files.newOutputStream(temporary)) {
            for (MultipartPart part : parts) {
                Path source = multipartDirectory(uploadId).resolve("parts")
                        .resolve(Integer.toString(part.partNumber()));
                try (InputStream input = Files.newInputStream(source)) {
                    total += copy(input, output, digest);
                }
            }
        }
        return new WriteResult(total, java.util.HexFormat.of().formatHex(digest.digest()));
    }

    private static long copy(InputStream input, OutputStream output, MessageDigest digest)
            throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return total;
    }

    private WriteResult streamPart(InputStream input, long expected, Path temporary)
            throws IOException {
        MessageDigest digest = sha256();
        try (OutputStream output = Files.newOutputStream(temporary)) {
            long total = copyLimited(input, output, digest, expected);
            if (input.read() != -1 || total != expected) {
                throw failure(FileStorageErrorCode.MULTIPART_INVALID,
                        "multipart part content length is invalid", null);
            }
            return new WriteResult(total, java.util.HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static long copyLimited(InputStream input, OutputStream output, MessageDigest digest,
                                    long expected) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while (total < expected && (read = input.read(buffer, 0,
                (int) Math.min(buffer.length, expected - total))) != -1) {
            total += read;
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static long expectedPartSize(MultipartUploadSession session, int partNumber) {
        return partNumber == session.partCount()
                ? session.totalSize() - session.partSize() * (session.partCount() - 1L)
                : session.partSize();
    }

    private static void validatePartNumber(int partNumber, MultipartUploadSession session) {
        if (partNumber < 1 || partNumber > session.partCount()) {
            throw failure(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart part number is invalid", null);
        }
    }

    private int cleanupIfExpired(Path directory, Instant now) {
        try (InputStream input = Files.newInputStream(directory.resolve("session.properties"))) {
            Properties values = new Properties();
            values.load(input);
            if (Instant.parse(values.getProperty("expiresAt")).isBefore(now)) {
                deleteTemporaryDirectory(directory);
                return 1;
            }
            return 0;
        } catch (IOException | RuntimeException exception) {
            return 0;
        }
    }

    private Path multipartDirectory(String uploadId) {
        if (uploadId == null || !uploadId.matches("[A-Za-z0-9-]{1,64}")) {
            throw failure(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart upload id is invalid", null);
        }
        return multipartRoot.resolve(uploadId).normalize();
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "multipart root is unavailable", exception);
        }
    }

    /**
     * 解析已存在目录的真实路径，统一符号链接别名后的目录比较语义。
     *
     * @param path 待规范化的目录路径
     * @return 可用的标准化目录路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private static Path resolveExistingPath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "multipart root is unavailable", exception);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(LocalFileStorageAdapter::deleteTemporary);
        } catch (IOException ignored) {
            // 临时会话清理失败由下一轮清理重试。
        }
    }

    private static void closeQuietly(InputStream content) {
        try {
            content.close();
        } catch (IOException ignored) {
            // 分片失败优先返回稳定业务错误。
        }
    }

    private record SessionData(MultipartUploadSession session, boolean completed,
                               List<MultipartPart> parts) {
    }

    private ObjectMetadata writeObject(UploadRequest request, Path target, InputStream content)
            throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            WriteResult result = streamToTemporary(content, temporary);
            removeMetadata(request.key());
            atomicMove(temporary, target);
            ObjectMetadata metadata = new ObjectMetadata(request.key(), result.length(),
                    request.contentType(), result.etag(), Instant.now(), request.cacheControl());
            writeMetadata(metadata);
            return metadata;
        } finally {
            deleteTemporary(temporary);
        }
    }

    private WriteResult streamToTemporary(InputStream input, Path temporary) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxSizeBytes) {
                    throw failure(FileStorageErrorCode.OBJECT_TOO_LARGE,
                            "upload exceeds maximum size", null);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return new WriteResult(total, java.util.HexFormat.of().formatHex(digest.digest()));
    }

    private ObjectMetadata readMetadata(ObjectKey key, Path path) throws IOException {
        java.util.Properties values = metadataStore.load(key);
        long length = Files.size(path);
        String contentType = values.getProperty("contentType", DEFAULT_CONTENT_TYPE);
        String etag = values.getProperty("etag");
        if (etag == null || etag.isBlank()) {
            etag = calculateEtag(path);
        }
        Instant modified = parseInstant(values.getProperty("lastModified"), path);
        CacheControl cacheControl = parseCacheControl(values.getProperty("cacheControl"));
        return new ObjectMetadata(key, length, contentType, etag, modified, cacheControl);
    }

    private void writeMetadata(ObjectMetadata metadata) throws IOException {
        metadataStore.write(metadata);
    }

    private void removeMetadata(ObjectKey key) throws IOException {
        metadataStore.delete(key);
    }

    private String calculateEtag(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static Instant parseInstant(String value, Path path) throws IOException {
        if (value != null) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ignored) {
                // 损坏元数据使用文件系统时间，避免阻断旧文件读取。
            }
        }
        return Files.getLastModifiedTime(path).toInstant();
    }

    private static CacheControl parseCacheControl(String value) {
        if (value == null || value.isBlank()) {
            return CacheControl.privateNoCache();
        }
        try {
            return new CacheControl(value);
        } catch (IllegalArgumentException ignored) {
            return CacheControl.privateNoCache();
        }
    }

    private void validateRequest(UploadRequest request) {
        validateUploadPolicy(request.contentLength(), request.contentType());
    }

    private void validateRequestOrClose(UploadRequest request) {
        try {
            validateRequest(request);
        } catch (FileStorageException exception) {
            closeRejectedRequest(request, exception);
            throw exception;
        }
    }

    private void validateUploadPolicy(long contentLength, String contentType) {
        if (contentLength > maxSizeBytes) {
            throw failure(FileStorageErrorCode.OBJECT_TOO_LARGE,
                    "upload exceeds maximum size", null);
        }
        if (!contentTypeWhitelist.isEmpty() && !contentTypeWhitelist.contains(contentType)) {
            throw failure(FileStorageErrorCode.CONTENT_TYPE_NOT_ALLOWED,
                    "content type is not allowed", null);
        }
        if (contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            throw failure(FileStorageErrorCode.INVALID_REQUEST,
                    "content type is invalid", null);
        }
    }

    private void validatePresignUpload(PresignUploadRequest request) {
        Objects.requireNonNull(request, "request");
        validateUploadPolicy(request.maxSizeBytes(), request.contentType());
    }

    private static void closeRejectedRequest(UploadRequest request,
                                             FileStorageException primaryException) {
        try {
            request.content().close();
        } catch (IOException closeException) {
            primaryException.addSuppressed(closeException);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw failure(FileStorageErrorCode.INTERNAL_ERROR,
                    "SHA-256 is unavailable", exception);
        }
    }

    static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw exception;
        }
    }

    static void deleteTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // 临时文件清理失败不覆盖原始操作错误。
        }
    }

    private static FileStorageException failure(FileStorageErrorCode code,
                                                String message, Throwable cause) {
        return new FileStorageException(code, message, cause);
    }

    /** 前缀删除的对象和目录统计。 */
    private static final class DeleteSummary {
        private int deletedFiles;
        private int deletedDirectories;
        private int skippedDirectories;
        private int failedDeletions;
    }

    /** 单个目录删除在并发和非空场景下的结果。 */
    private enum DeleteDirectoryResult {
        DELETED,
        ABSENT,
        NON_EMPTY,
        SKIPPED,
        FAILED
    }

    private record WriteResult(long length, String etag) {
    }
}
