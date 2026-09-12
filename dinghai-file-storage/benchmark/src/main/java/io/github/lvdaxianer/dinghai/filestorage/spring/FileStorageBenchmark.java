package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioStorageConfiguration;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 对 Local 与 MinIO 执行真实文件并发上传、读取和完整性校验的命令行压测工具。 */
public final class FileStorageBenchmark {
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final String MINIO_ENDPOINT = "DINGHAI_FILE_STORAGE_MINIO_ENDPOINT";
    private static final String MINIO_ACCESS_KEY = "DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY";
    private static final String MINIO_SECRET_KEY = "DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY";
    private static final String MINIO_BUCKET = "DINGHAI_FILE_STORAGE_MINIO_BUCKET";

    /** 执行压测。 */
    public static void main(String[] arguments) throws Exception {
        FileStorageBenchmarkOptions options = FileStorageBenchmarkOptions.parse(arguments);
        new FileStorageBenchmark().run(options);
    }

    /** 对指定 provider 运行并发写入、冷读和可选的热读。 */
    void run(FileStorageBenchmarkOptions options) throws Exception {
        Files.createDirectories(options.localRoot());
        Path localRoot = Files.createTempDirectory(options.localRoot(), "run-");
        FileStorage storage = null;
        List<ObjectKey> keys = List.of();
        try {
            storage = storage(options, localRoot);
            FileStorage activeStorage = storage;
            keys = keys(options.concurrency());
            String digest = digest(options.file());
            print("source", options, Files.size(options.file()), digest);
            measure("upload", options, keys, key -> upload(activeStorage, options.file(), key));
            measure("read-cold", options, repeated(keys, options.readRounds()), key -> read(activeStorage, key, digest));
            if (options.provider() == FileStorageBenchmarkOptions.Provider.MINIO) {
                measureCached(activeStorage, options, keys, digest, localRoot);
            } else {
                print("read-hot", options, 0L, "not-applicable");
            }
        } finally {
            cleanup(storage, keys);
            deleteTree(localRoot);
        }
    }

    /** 创建所选 provider 的隔离存储。 */
    private FileStorage storage(FileStorageBenchmarkOptions options, Path localRoot) {
        if (options.provider() == FileStorageBenchmarkOptions.Provider.LOCAL) {
            return new LocalFileStorageAdapter(localRoot.resolve("objects"), "benchmark-key".getBytes(),
                    Filesize.unlimited(), java.util.Set.of(), Duration.ofHours(1), java.time.Clock.systemUTC());
        } else {
            return minioStorage();
        }
    }

    /** 从运行时环境构造 MinIO 存储，不输出敏感值。 */
    private FileStorage minioStorage() {
        String endpoint = requiredEnv(MINIO_ENDPOINT);
        String bucket = requiredEnv(MINIO_BUCKET);
        String accessKey = requiredEnv(MINIO_ACCESS_KEY);
        String secretKey = requiredEnv(MINIO_SECRET_KEY);
        ensureBucket(endpoint, bucket, accessKey, secretKey);
        return new MinioFileStorageAdapter(new MinioStorageConfiguration(endpoint, bucket, accessKey, secretKey,
                Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(60)));
    }

    /** 创建不存在的专用 benchmark bucket，不修改既有 bucket 的访问策略。 */
    private void ensureBucket(String endpoint, String bucket, String accessKey, String secretKey) {
        try {
            MinioClient client = MinioClient.builder().endpoint(endpoint)
                    .credentials(accessKey, secretKey).build();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO benchmark bucket is unavailable", exception);
        }
    }

    /** 将源文件流式上传到独立对象 key。 */
    private void upload(FileStorage storage, Path file, ObjectKey key) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            storage.put(new UploadRequest(key, input, Files.size(file), "application/octet-stream",
                    CacheControl.privateNoCache()));
        }
    }

    /** 流式读取对象并验证 SHA-256。 */
    private void read(FileStorage storage, ObjectKey key, String expectedDigest) throws IOException {
        try (DownloadObject object = storage.get(key).orElseThrow()) {
            if (!digest(object.stream()).equals(expectedDigest)) {
                throw new IOException("content digest mismatch");
            } else {
                return;
            }
        }
    }

    /** 从二级缓存读取对象并验证 SHA-256。 */
    private void read(ProviderFileReadCache cache, ObjectKey key, String expectedDigest) throws IOException {
        try (DownloadObject object = cache.get(key).orElseThrow()) {
            if (!digest(object.stream()).equals(expectedDigest)) {
                throw new IOException("content digest mismatch");
            } else {
                return;
            }
        }
    }

    /** 测量 MinIO 二级缓存的热读性能。 */
    private void measureCached(FileStorage storage, FileStorageBenchmarkOptions options,
                               List<ObjectKey> keys, String digest, Path localRoot) throws Exception {
        ProviderFileReadCache cache = new ProviderFileReadCache(storage, "benchmark", localRoot.resolve("cache"),
                true, Files.size(options.file()), BUFFER_SIZE, options.concurrency(), options.concurrency() * 2);
        try {
            measure("cache-fill", options, keys, key -> read(cache, key, digest));
            cache.awaitIdle(5, TimeUnit.MINUTES);
            measure("read-hot", options, repeated(keys, options.readRounds()), key -> read(cache, key, digest));
        } finally {
            cache.close();
        }
    }

    /** 并发执行同类操作并输出汇总指标。 */
    private void measure(String phase, FileStorageBenchmarkOptions options, List<ObjectKey> keys,
                         CheckedConsumer<ObjectKey> task) throws Exception {
        ExecutorService executor = new ThreadPoolExecutor(options.concurrency(), options.concurrency(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(keys.size()), runnable -> {
            Thread thread = new Thread(runnable, "file-storage-benchmark-");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        Instant started = Instant.now();
        List<Future<Long>> futures = submit(executor, keys, task);
        List<Long> latencies = collect(futures);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();
        print(phase, options, elapsedMillis, metrics(latencies, bytes(keys, options), elapsedMillis));
    }

    /** 提交独立对象任务。 */
    private List<Future<Long>> submit(ExecutorService executor, List<ObjectKey> keys,
                                      CheckedConsumer<ObjectKey> task) {
        List<Future<Long>> futures = new ArrayList<>(keys.size());
        for (ObjectKey key : keys) {
            futures.add(executor.submit(timed(key, task)));
        }
        return futures;
    }

    /** 对单个任务记录耗时。 */
    private Callable<Long> timed(ObjectKey key, CheckedConsumer<ObjectKey> task) {
        return () -> {
            long started = System.nanoTime();
            task.accept(key);
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        };
    }

    /** 等待任务完成并收集延迟。 */
    private List<Long> collect(List<Future<Long>> futures) throws Exception {
        List<Long> latencies = new ArrayList<>(futures.size());
        for (Future<Long> future : futures) {
            latencies.add(future.get());
        }
        return latencies;
    }

    /** 返回每个上传对象并按读取轮次展开的任务列表。 */
    private List<ObjectKey> repeated(List<ObjectKey> keys, int rounds) {
        List<ObjectKey> result = new ArrayList<>(keys.size() * rounds);
        for (int index = 0; index < rounds; index++) {
            result.addAll(keys);
        }
        return result;
    }

    /** 创建一次运行专用对象 key。 */
    private List<ObjectKey> keys(int concurrency) {
        String runId = UUID.randomUUID().toString();
        List<ObjectKey> keys = new ArrayList<>(concurrency);
        for (int index = 0; index < concurrency; index++) {
            keys.add(ObjectKey.of("benchmark/" + runId + "/" + index));
        }
        return keys;
    }

    /** 尽力删除本次运行创建的对象。 */
    private void cleanup(FileStorage storage, List<ObjectKey> keys) {
        for (ObjectKey key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                // 清理失败不应掩盖压测结果；对象 key 带运行 ID，便于人工删除。
            }
        }
    }

    /** 计算文件的 SHA-256。 */
    private String digest(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return digest(input);
        }
    }

    /** 流式计算输入流的 SHA-256。 */
    private String digest(InputStream input) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 构造 SHA-256 计算器。 */
    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 输出无敏感信息的压测摘要。 */
    private void print(String phase, FileStorageBenchmarkOptions options, long value, String details) {
        System.out.printf("phase=%s provider=%s file=%s concurrency=%d readRounds=%d elapsedMs=%d %s%n",
                phase, options.provider(), options.file().getFileName(), options.concurrency(),
                options.readRounds(), value, details);
    }

    /** 计算延迟统计值。 */
    private String metrics(List<Long> values, long bytes, long elapsedMillis) {
        values.sort(Long::compareTo);
        return "operations=" + values.size() + " avgMs=" + average(values) + " p95Ms="
                + percentile(values, 95) + " p99Ms=" + percentile(values, 99) + " throughputMiBps="
                + throughput(bytes, elapsedMillis) + " errors=0";
    }

    /** 返回该阶段传输的总字节数。 */
    private long bytes(List<ObjectKey> keys, FileStorageBenchmarkOptions options) throws IOException {
        return Math.multiplyExact(Files.size(options.file()), keys.size());
    }

    /** 返回以 MiB/s 表示的总吞吐量。 */
    private long throughput(long bytes, long elapsedMillis) {
        return elapsedMillis == 0L ? 0L : bytes * 1000L / elapsedMillis / (1024L * 1024L);
    }

    /** 计算平均延迟。 */
    private long average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum() / values.size();
    }

    /** 返回 nearest-rank 百分位。 */
    private long percentile(List<Long> values, int percentile) {
        return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * percentile / 100.0) - 1));
    }

    /** 返回必需且非空的环境变量值，错误信息不包含变量值。 */
    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing required environment variable: " + name);
        }
        return value;
    }

    /** 递归删除 Local benchmark 临时目录。 */
    private void deleteTree(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::delete);
            }
        }
    }

    /** 删除一个临时路径。 */
    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时目录会由操作系统清理，单文件删除失败不改变压测结论。
        }
    }

    /** 允许任务抛出检查异常的消费函数。 */
    @FunctionalInterface
    private interface CheckedConsumer<T> { void accept(T value) throws Exception; }

    /** 集中表达本地压测不限制单对象大小。 */
    private static final class Filesize {
        private Filesize() { }
        private static long unlimited() { return Long.MAX_VALUE; }
    }
}
