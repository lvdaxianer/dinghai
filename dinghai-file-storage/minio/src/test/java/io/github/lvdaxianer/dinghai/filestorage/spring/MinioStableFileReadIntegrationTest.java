package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker MinIO 私有 bucket 的稳定读取验收测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class MinioStableFileReadIntegrationTest {
    /** 启用 Docker MinIO 验收测试的环境变量。 */
    private static final String ENABLED = "DINGHAI_FILE_STORAGE_MINIO_IT";
    /** Docker MinIO 端点环境变量。 */
    private static final String ENDPOINT = "DINGHAI_FILE_STORAGE_MINIO_ENDPOINT";
    /** Docker MinIO 访问密钥环境变量。 */
    private static final String ACCESS_KEY = "DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY";
    /** Docker MinIO 私密访问密钥环境变量。 */
    private static final String SECRET_KEY = "DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY";
    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    /** 端到端迁移用的本地目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Local 导出包导入 MinIO 后必须保持稳定 ID 并可读取私有对象。
     *
     * @throws Exception 创建、迁移、读取和清理临时资源失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldImportLocalPackageToMinioWithUnchangedStableId() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        MinioClient client = client();
        String bucket = temporaryBucket();
        ObjectKey key = ObjectKey.of("1/1/1/private.txt");
        byte[] content = "local-to-minio".getBytes(StandardCharsets.UTF_8);
        Path root = temporaryFolder.getRoot().toPath();
        LocalFileStorageAdapter local = new LocalFileStorageAdapter(root.resolve("local"),
                "local-signing-key".getBytes(StandardCharsets.UTF_8));
        local.put(new UploadRequest(key, new ByteArrayInputStream(content), content.length,
                "text/plain", new CacheControl("private, max-age=60")));
        FileStorageMigrationService exporter = new FileStorageMigrationService(local, "documents",
                root.resolve("exports"), new com.fasterxml.jackson.databind.ObjectMapper());
        Path packagePath = exporter.exportPackage("release-001");
        Path importRoot = root.resolve("imports");
        Files.createDirectories(importRoot);
        Files.move(packagePath, importRoot.resolve("release-001"));
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try {
            MinioFileStorageAdapter minio = new MinioFileStorageAdapter(client, bucket, endpoint());
            FileStorageMigrationService importer = new FileStorageMigrationService(minio, "documents",
                    root.resolve("unused-export"), new com.fasterxml.jackson.databind.ObjectMapper());
            importer.importPackage("release-001", importRoot, root.resolve("completed"),
                    root.resolve("failed"));
            StableFileAccessService stableAccess = new StableFileAccessService("documents",
                    "stable-file-id-test-key-material-32b".getBytes(StandardCharsets.UTF_8));
            String payload = stableAccess.identifier(key).substring("v1.".length());
            MockMvc mockMvc = webClient(minio, stableAccess);

            MvcResult started = mockMvc.perform(get("/files/read/v1/preview/" + payload))
                    .andExpect(request().asyncStarted()).andReturn();

            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("inline")))
                    .andExpect(header().string("Cache-Control", "private, max-age=60"))
                    .andExpect(content().string("local-to-minio"));
        } finally {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * 私有 MinIO 对象应通过稳定预览地址内联流式返回。
     *
     * @throws Exception 创建、读取和清理临时 bucket 失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldPreviewPrivateMinioObjectThroughStableId() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        MinioClient client = client();
        String bucket = temporaryBucket();
        ObjectKey key = ObjectKey.of("reports/private.txt");
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try {
            MinioFileStorageAdapter storage = new MinioFileStorageAdapter(client, bucket, endpoint());
            byte[] content = "private-minio-content".getBytes(StandardCharsets.UTF_8);
            storage.put(new UploadRequest(key, new ByteArrayInputStream(content), content.length,
                    "text/plain", CacheControl.privateNoCache()));
            StableFileAccessService stableAccess = new StableFileAccessService("documents",
                    "stable-file-id-test-key-material-32b".getBytes(StandardCharsets.UTF_8));
            MockMvc mockMvc = webClient(storage, stableAccess);
            String payload = stableAccess.identifier(key).substring("v1.".length());

            MvcResult started = mockMvc.perform(get("/files/read/v1/preview/" + payload))
                    .andExpect(request().asyncStarted()).andReturn();

            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.containsString("inline")))
                    .andExpect(content().string("private-minio-content"));
        } finally {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * MinIO 对象应能通过显式启用的历史真实 key 路由读取。
     *
     * @throws Exception 创建、读取和清理临时 bucket 失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldReadPrivateMinioObjectThroughEnabledLegacyRoute() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        MinioClient client = client();
        String bucket = temporaryBucket();
        ObjectKey key = ObjectKey.of("reports/legacy/private.txt");
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try {
            MinioFileStorageAdapter storage = new MinioFileStorageAdapter(client, bucket, endpoint());
            put(storage, key, "legacy-minio-content");
            StableFileAccessService stableAccess = new StableFileAccessService("documents",
                    "stable-file-id-test-key-material-32b".getBytes(StandardCharsets.UTF_8));
            FileStorageProperties.Web web = new FileStorageProperties.Web();
            web.setLegacyReadEnabled(true);
            FileStorageWebRoutes routes = FileStorageWebRoutes.from(web);
            MockMvc mockMvc = webClient(storage, stableAccess, routes);

            MvcResult started = mockMvc.perform(get("/files/reports/legacy/private.txt"))
                    .andExpect(request().asyncStarted()).andReturn();

            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                    .andExpect(content().string("legacy-minio-content"));
        } finally {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * MinIO 对象首次直通后应异步落盘，远程变更时缓存必须失效。
     *
     * @throws Exception 创建、读取和清理临时 bucket 失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldFillAndInvalidateDiskCacheForMinioObject() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(System.getenv(ENABLED)));
        MinioClient client = client();
        String bucket = temporaryBucket();
        ObjectKey key = ObjectKey.of("reports/cache.txt");
        Path cacheRoot = Files.createTempDirectory("dinghai-minio-cache-");
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try {
            MinioFileStorageAdapter storage = new MinioFileStorageAdapter(client, bucket, endpoint());
            put(storage, key, "first");
            ProviderFileReadCache cache = new ProviderFileReadCache(storage, "documents", cacheRoot,
                    true, 1024L);
            try (var object = cache.get(key).orElseThrow()) {
                org.junit.Assert.assertEquals("first", new String(object.stream().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            cache.awaitIdle(2L, TimeUnit.SECONDS);
            org.junit.Assert.assertTrue(cache.hasCachedObject(key));
            put(storage, key, "second");

            try (var object = cache.get(key).orElseThrow()) {
                org.junit.Assert.assertEquals("second", new String(object.stream().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            cache.close();
        } finally {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
            deleteRecursively(cacheRoot);
        }
    }

    /** 创建连接到用户启动的 Docker MinIO 的客户端。 */
    private MinioClient client() {
        return MinioClient.builder().endpoint(endpoint()).credentials(required(ACCESS_KEY),
                required(SECRET_KEY)).build();
    }

    /** 创建稳定读取的 MockMvc 客户端。 */
    private MockMvc webClient(MinioFileStorageAdapter storage, StableFileAccessService stableAccess) {
        return webClient(storage, stableAccess, FileStorageWebRoutes.from(new FileStorageProperties.Web()));
    }

    /** 创建使用指定配置化路由的 MockMvc 客户端。 */
    private MockMvc webClient(MinioFileStorageAdapter storage, StableFileAccessService stableAccess,
                              FileStorageWebRoutes routes) {
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, null, new WebTransferConfiguration(128 * 1024, 128, 16, 0, null,
                routes, true));
        return FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
    }

    /** 将测试文本写入 MinIO 对象。 */
    private void put(MinioFileStorageAdapter storage, ObjectKey key, String value) {
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        storage.put(new UploadRequest(key, new ByteArrayInputStream(content), content.length,
                "text/plain", CacheControl.privateNoCache()));
    }

    /** 删除测试产生的缓存目录。 */
    private void deleteRecursively(Path root) throws Exception {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    /** 返回随机、受 bucket 规则约束的测试 bucket 名称。 */
    private String temporaryBucket() {
        return "dhstable" + UUID.randomUUID().toString().replace("-", "");
    }

    /** 读取 MinIO 端点。 */
    private String endpoint() {
        return required(ENDPOINT);
    }

    /** 读取必需且不输出的测试环境变量。 */
    private String required(String name) {
        String value = System.getenv(name);
        Assume.assumeTrue(value != null && !value.isBlank());
        return value;
    }
}
