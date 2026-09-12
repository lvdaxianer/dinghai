package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 为非本地 provider 提供元数据校验的二级磁盘读取缓存。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class ProviderFileReadCache implements AutoCloseable {
    /** 缓存复制缓冲区大小。 */
    private static final int DEFAULT_BUFFER_SIZE = 128 * 1024;
    /** 缓存异步线程数。 */
    private static final int DEFAULT_THREAD_COUNT = 4;
    /** 缓存异步队列容量。 */
    private static final int DEFAULT_QUEUE_CAPACITY = 32;
    /** 缓存 sidecar 文件后缀。 */
    private static final String SIDECAR_SUFFIX = ".properties";
    /** 始终使用的缓存版本。 */
    private static final String CACHE_VERSION = "1";
    private final FileStorage provider;
    private final String storageAlias;
    private final Path root;
    private final boolean enabled;
    private final long maxObjectSize;
    private final int bufferSize;
    private final ThreadPoolExecutor executor;
    /** 正在异步填充的对象摘要。 */
    private final Set<String> filling = ConcurrentHashMap.newKeySet();

    /**
     * 创建二级缓存包装器。
     *
     * @param provider 远程文件存储
     * @param storageAlias 逻辑存储别名
     * @param root 缓存根目录
     * @param enabled 是否启用缓存
     * @param maxObjectSize 异步落盘最大对象大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ProviderFileReadCache(FileStorage provider, String storageAlias, Path root,
                                 boolean enabled, long maxObjectSize) {
        this(provider, storageAlias, root, enabled, maxObjectSize, DEFAULT_BUFFER_SIZE,
                DEFAULT_THREAD_COUNT, DEFAULT_QUEUE_CAPACITY);
    }

    public ProviderFileReadCache(FileStorage provider, String storageAlias, Path root,
                                 boolean enabled, long maxObjectSize, int bufferSize,
                                 int maxConcurrentFills, int queueCapacity) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.storageAlias = Objects.requireNonNull(storageAlias, "storageAlias");
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (maxObjectSize < 0L) {
            throw new IllegalArgumentException("max object size must be non-negative");
        }
        if (bufferSize <= 0 || maxConcurrentFills <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("cache resource limits must be positive");
        }
        this.enabled = enabled;
        this.maxObjectSize = maxObjectSize;
        this.bufferSize = bufferSize;
        createRoot();
        executor = new ThreadPoolExecutor(maxConcurrentFills, maxConcurrentFills, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "file-storage-cache-");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 获取对象，命中缓存前先校验远端元数据。 */
    public Optional<DownloadObject> get(ObjectKey key) {
        if (!enabled) {
            return provider.get(key);
        }
        Optional<ObjectMetadata> remote = provider.stat(key);
        if (remote.isEmpty()) {
            removeCachedObject(key);
            return Optional.empty();
        }
        Optional<DownloadObject> cached = cached(remote.get());
        if (cached.isPresent()) {
            return cached;
        }
        Optional<DownloadObject> current = provider.get(key);
        current.ifPresent(download -> scheduleFill(key, remote.get()));
        return current;
    }

    /** 查询远程元数据。 */
    public Optional<ObjectMetadata> stat(ObjectKey key) { return provider.stat(key); }

    /** 写入对象后清除可能的旧缓存。 */
    public ObjectMetadata put(UploadRequest request) {
        ObjectMetadata metadata = provider.put(request);
        removeCachedObject(request.key());
        return metadata;
    }

    /** 删除对象后清除可能的旧缓存。 */
    public boolean delete(ObjectKey key) {
        boolean deleted = provider.delete(key);
        removeCachedObject(key);
        return deleted;
    }

    /** 等待当前缓存任务完成，仅供测试和生命周期检查使用。 */
    void awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline
                && (executor.getActiveCount() > 0 || !executor.getQueue().isEmpty())) {
            Thread.sleep(10L);
        }
    }

    /** 判断对象是否已生成完整缓存文件。 */
    boolean hasCachedObject(ObjectKey key) { return Files.isRegularFile(dataPath(key)); }

    /**
     * 清理最后访问时间已超出 idle TTL 的完整缓存副本。
     *
     * @param now 当前时间
     * @param idleTtl 空闲保留时间
     */
    void cleanExpired(Instant now, Duration idleTtl) {
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(SIDECAR_SUFFIX))
                    .forEach(path -> cleanSidecar(path, now, idleTtl));
        } catch (IOException ignored) {
            // 下个午夜任务会重试，清理失败不影响读取请求。
        }
    }

    /** 关闭缓存异步执行器。 */
    @Override public void close() {
        executor.shutdown();
    }

    /** 创建缓存根目录。 */
    private void createRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cache root is unavailable", exception);
        }
    }

    /** 尝试读取并校验完整缓存副本。 */
    private Optional<DownloadObject> cached(ObjectMetadata remote) {
        Path data = dataPath(remote.key());
        Path sidecar = sidecarPath(remote.key());
        if (!Files.isRegularFile(data) || !Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        try {
            Properties values = load(sidecar);
            if (!matches(values, remote) || Files.size(data) != remote.contentLength()) {
                removeCachedObject(remote.key());
                return Optional.empty();
            }
            touch(values, sidecar);
            return Optional.of(new DownloadObject(Files.newInputStream(data), remote));
        } catch (IOException exception) {
            removeCachedObject(remote.key());
            return Optional.empty();
        }
    }

    /** 将当前远程对象加入有界异步填充队列。 */
    private void scheduleFill(ObjectKey key, ObjectMetadata expected) {
        if (expected.contentLength() > maxObjectSize) {
            return;
        }
        String digest = digest(key);
        if (!filling.add(digest)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    fill(key, expected);
                } finally {
                    filling.remove(digest);
                }
            });
        } catch (RejectedExecutionException ignored) {
            filling.remove(digest);
            // 队列满时保持当前请求直通，不阻塞远程读取。
        }
    }

    /** 使用第二条 provider 流写入临时文件并原子发布。 */
    private void fill(ObjectKey key, ObjectMetadata expected) {
        Path temporary = root.resolve(dataPath(key).getFileName() + ".tmp-" + Thread.currentThread().getId());
        try (DownloadObject download = provider.get(key).orElseThrow();
             InputStream input = download.stream();
             OutputStream output = Files.newOutputStream(temporary)) {
            copy(input, output);
            ObjectMetadata current = provider.stat(key).orElseThrow();
            if (!sameMetadata(expected, current) || Files.size(temporary) != current.contentLength()) {
                return;
            }
            Files.move(temporary, dataPath(key), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            writeSidecar(current);
        } catch (Exception ignored) {
            // 缓存填充失败不得影响已返回的当前请求。
        } finally {
            delete(temporary);
        }
    }

    /** 复制对象流。 */
    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[bufferSize];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    /** 写入不包含对象 key 的 sidecar。 */
    private void writeSidecar(ObjectMetadata metadata) throws IOException {
        Properties values = new Properties();
        values.setProperty("version", CACHE_VERSION);
        values.setProperty("etag", metadata.etag());
        values.setProperty("length", Long.toString(metadata.contentLength()));
        values.setProperty("lastModified", metadata.lastModified().toString());
        values.setProperty("lastAccessAt", Instant.now().toString());
        Path target = sidecarPath(metadata.key());
        Path temporary = root.resolve(target.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, null);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /** 读取 sidecar 属性。 */
    private Properties load(Path sidecar) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(sidecar)) {
            values.load(input);
        }
        return values;
    }

    /** 判断 sidecar 与远程元数据是否完全一致。 */
    private boolean matches(Properties values, ObjectMetadata metadata) {
        return CACHE_VERSION.equals(values.getProperty("version"))
                && metadata.etag().equals(values.getProperty("etag"))
                && Long.toString(metadata.contentLength()).equals(values.getProperty("length"))
                && metadata.lastModified().toString().equals(values.getProperty("lastModified"));
    }

    /** 更新 sidecar 最后访问时间。 */
    private void touch(Properties values, Path sidecar) throws IOException {
        values.setProperty("lastAccessAt", Instant.now().toString());
        try (OutputStream output = Files.newOutputStream(sidecar)) {
            values.store(output, null);
        }
    }

    /** 删除对象对应的缓存文件和 sidecar。 */
    private void removeCachedObject(ObjectKey key) {
        delete(dataPath(key));
        delete(sidecarPath(key));
    }

    /** 删除单个路径并忽略清理失败。 */
    private void delete(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { }
    }

    /** 清理已过期 sidecar 对应的数据和元数据文件。 */
    private void cleanSidecar(Path sidecar, Instant now, Duration idleTtl) {
        try {
            Instant lastAccessAt = Instant.parse(load(sidecar).getProperty("lastAccessAt"));
            if (lastAccessAt.plus(idleTtl).isBefore(now)) {
                String name = sidecar.getFileName().toString();
                String digest = name.substring(0, name.length() - SIDECAR_SUFFIX.length());
                delete(root.resolve(digest + ".data"));
                delete(sidecar);
            }
        } catch (RuntimeException | IOException ignored) {
            // 损坏 sidecar 保留到下一轮，避免错误清理有效缓存副本。
        }
    }

    /** 生成不泄露对象 key 的缓存文件路径。 */
    private Path dataPath(ObjectKey key) { return root.resolve(digest(key) + ".data"); }
    /** 生成不泄露对象 key 的 sidecar 路径。 */
    private Path sidecarPath(ObjectKey key) { return root.resolve(digest(key) + SIDECAR_SUFFIX); }

    /** 对逻辑别名和 key 做 SHA-256。 */
    private String digest(ObjectKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(storageAlias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cache digest is unavailable", exception);
        }
    }

    /** 比较缓存填充前后的远程元数据。 */
    private boolean sameMetadata(ObjectMetadata left, ObjectMetadata right) {
        return left.contentLength() == right.contentLength()
                && left.etag().equals(right.etag())
                && left.lastModified().equals(right.lastModified());
    }
}
