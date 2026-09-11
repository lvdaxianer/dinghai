package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 基于本地磁盘的流式文件存储适配器。 */
public final class LocalFileStorageAdapter implements FileStorage {
    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    /** 本地预签名 URL 的默认最长有效期。 */
    public static final Duration DEFAULT_MAXIMUM_PRESIGN_EXPIRY = Duration.ofHours(1);
    private static final String DEFAULT_PATH_PREFIX = "/files";
    private final LocalObjectPathResolver paths;
    private final LocalMetadataStore metadataStore;
    private final LocalFileStorageSigner signer;
    private final long maxSizeBytes;
    private final Set<String> contentTypeWhitelist;
    private final String pathPrefix;

    /** 使用默认 100 MB 上限创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey) {
        this(root, signingKey, UploadRequest.DEFAULT_MAX_SIZE_BYTES);
    }

    /** 使用指定上传上限创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes) {
        this(root, signingKey, maxSizeBytes, Set.of(), DEFAULT_MAXIMUM_PRESIGN_EXPIRY,
                Clock.systemUTC());
    }

    /** 使用指定上传和签名策略创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock) {
        this(root, signingKey, maxSizeBytes, contentTypeWhitelist, maximumPresignExpiry, clock,
                DEFAULT_PATH_PREFIX);
    }

    /** 使用指定 Web 路径前缀创建本地适配器。 */
    public LocalFileStorageAdapter(Path root, byte[] signingKey, long maxSizeBytes,
                                   Set<String> contentTypeWhitelist,
                                   Duration maximumPresignExpiry, Clock clock, String pathPrefix) {
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
        if (!Files.isRegularFile(path)) {
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

    /** 获取对象元数据，不存在时返回空。 */
    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        Path path = paths.resolve(key);
        if (!Files.isRegularFile(path)) {
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
        return Files.isRegularFile(paths.resolve(key));
    }

    /** 删除对象和元数据；对象不存在时返回 false。 */
    @Override
    public boolean delete(ObjectKey key) {
        Path path = paths.resolve(key);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            Files.deleteIfExists(path);
            metadataStore.delete(key);
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object delete failed", exception);
        }
    }

    /** 生成本地预签名上传地址。 */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        validatePresignUpload(request);
        return signer.presignUpload(request, pathPrefix);
    }

    /** 生成本地预签名下载地址。 */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return signer.presignDownload(request, pathPrefix);
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

    private record WriteResult(long length, String etag) {
    }
}
