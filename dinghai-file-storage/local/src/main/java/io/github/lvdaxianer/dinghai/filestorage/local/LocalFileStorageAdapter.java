package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.LocalFileSource;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
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
import java.util.stream.Stream;

/** 基于本地磁盘的流式文件存储适配器。 */
public final class LocalFileStorageAdapter implements FileStorage, LocalFileSource,
        MultipartFileStorage {
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
    private final Path multipartRoot;
    private final Clock clock;

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
        this.clock = Objects.requireNonNull(clock, "clock");
        this.multipartRoot = paths.root().resolveSibling(paths.root().getFileName()
                + ".dinghai-multipart");
        createDirectory(multipartRoot);
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

    /** 惰性枚举根目录中的普通对象，关闭流时释放目录遍历句柄。 */
    @Override
    public Stream<ObjectMetadata> list() {
        try {
            Path root = paths.root();
            return Files.walk(root).filter(this::isRegularObjectFile).map(root::relativize)
                    .map(Path::toString).map(value -> value.replace(java.io.File.separatorChar, '/'))
                    .map(ObjectKey::of).map(key -> stat(key).orElseThrow());
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "local object listing failed", exception);
        }
    }

    /** 判断目录项是否为不跟随符号链接的普通对象文件。 */
    private boolean isRegularObjectFile(Path path) {
        return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
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
    public PresignedUpload presignPart(MultipartPartRequest request) {
        SessionData session = requireOpenOrCompleted(request.uploadId());
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
        String url = pathPrefix + "/multipart/" + request.uploadId()
                + "/parts/" + request.partNumber();
        return new PresignedUpload(url, "PUT", expiresAt,
                Map.of("Content-Type", session.session().contentType()), Map.of());
    }

    /** 将一个分片先写临时文件，再原子替换为已确认分片。 */
    @Override
    public MultipartPart uploadPart(MultipartPartRequest request, InputStream content,
                                    long contentLength) {
        Objects.requireNonNull(content, "content");
        SessionData session = requireOpen(request.uploadId());
        validatePartNumber(request.partNumber(), session.session());
        long expected = expectedPartSize(session.session(), request.partNumber());
        if (contentLength != expected) {
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
    public List<MultipartPart> listParts(String uploadId) {
        SessionData session = requireSession(uploadId);
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
    public synchronized ObjectMetadata complete(String uploadId, MultipartCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        SessionData session = requireSession(uploadId);
        if (session.completed()) {
            return stat(session.session().key()).orElseThrow();
        }
        if (!session.session().expiresAt().isAfter(clock.instant())) {
            abort(uploadId);
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
    public synchronized void abort(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return;
        }
        deleteTemporaryDirectory(multipartDirectory(uploadId));
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

    private SessionData requireOpen(String uploadId) {
        SessionData data = requireSession(uploadId);
        if (data.completed()) {
            throw failure(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart session is already completed", null);
        }
        if (!data.session().expiresAt().isAfter(clock.instant())) {
            abort(uploadId);
            throw failure(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired", null);
        }
        return data;
    }

    private SessionData requireOpenOrCompleted(String uploadId) {
        SessionData data = requireSession(uploadId);
        if (!data.session().expiresAt().isAfter(clock.instant()) && !data.completed()) {
            abort(uploadId);
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
        return listParts(session.session().uploadId()).stream()
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

    private record WriteResult(long length, String etag) {
    }
}
