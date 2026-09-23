package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileReference;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import java.io.ByteArrayInputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 本地文件存储签名 Web 端点测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageWebControllerTest {

    /** 固定签名时间。 */
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    /** 测试 HMAC 密钥。 */
    private static final byte[] SIGNING_KEY = "starter-web-signing-key".getBytes(StandardCharsets.UTF_8);

    /** 测试临时目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 本地磁盘存储。 */
    private LocalFileStorageAdapter storage;

    /** MVC 测试客户端。 */
    private MockMvc mockMvc;
    /** 稳定文件读取 ID 编解码器。 */
    private StableFileAccessService stableAccess;
    /** multipart JSON 响应读取器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建带默认拒绝访问策略的端点。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Before
    public void setUp() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        storage = new LocalFileStorageAdapter(temporaryFolder.newFolder("storage").toPath(),
                SIGNING_KEY, 1024L, java.util.Set.of(), Duration.ofHours(1L), clock, "/files");
        write(ObjectKey.of("documents/readme.txt"), "hello", "text/plain",
                new CacheControl("public, max-age=60"));
        stableAccess = new StableFileAccessService("documents",
                "stable-file-id-test-key-material-32b".getBytes(StandardCharsets.UTF_8));
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), clock, stableAccess);
        FileStorageAccessPolicy multipartPolicy = (principal, key, operation) ->
                operation == FileStorageAccessOperation.MULTIPART_UPLOAD;
        MultipartUploadController multipartController = new MultipartUploadController(
                new MultipartUploadService(storage, multipartPolicy, new FileStorageProperties(), clock));
        mockMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller, multipartController)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
    }

    /**
     * 有效上传签名应限制类型和大小并流式写入对象。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldUploadThroughValidPresignedUrl() throws Exception {
        PresignedUpload upload = storage.presignUpload(new PresignUploadRequest(
                ObjectKey.of("uploads/new.txt"), Duration.ofMinutes(10L), "text/plain", 20L));

        mockMvc.perform(put(upload.url()).contentType(MediaType.TEXT_PLAIN).content("new content"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/files/read/key/preview/uploads/new.txt"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key.value").value("uploads/new.txt"))
                .andExpect(jsonPath("$.contentLength").value(11))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.etag").isNotEmpty())
                .andExpect(jsonPath("$.lastModified").isNotEmpty())
                .andExpect(jsonPath("$.cacheControl.value").value("private, max-age=0"))
                .andExpect(jsonPath("$.previewUrl").value("/files/read/key/preview/uploads/new.txt"));

        assertEquals("new content", read(ObjectKey.of("uploads/new.txt")));
    }

    /**
     * multipart 会话创建端点必须可用于已授权上传。
     *
     * @throws Exception MVC 请求执行失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldInitiateMultipartUploadForAuthorizedPrincipal() throws Exception {
        mockMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"uploads/large.bin\",\"size\":10485760,"
                                + "\"partSize\":10485760,\"contentType\":\"application/octet-stream\","
                                + "\"expiresIn\":\"PT10M\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.key.value").value("uploads/large.bin"));
    }

    /** 未授权创建必须在调用 provider 前被拒绝。 */
    @Test
    public void shouldRejectUnauthorizedMultipartInitiationBeforeProviderCall() throws Exception {
        MultipartFileStorage multipartStorage = multipartStorageMock();
        MockMvc deniedMvc = multipartMvc(multipartStorage, (principal, key, operation) -> false);

        deniedMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content(multipartInitiateJson("uploads/no.bin")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_PRESIGN"));

        verify(multipartStorage, never()).initiate(any(MultipartUploadRequest.class));
    }

    /** 不符合已配置分片策略的创建请求必须在调用 provider 前被拒绝。 */
    @Test
    public void shouldRejectMultipartInitiationOutsideConfiguredLimitsBeforeProviderCall() throws Exception {
        MultipartFileStorage multipartStorage = multipartStorageMock();
        MockMvc limitedMvc = multipartMvc(multipartStorage, (principal, key, operation) -> true);

        limitedMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"uploads/invalid.bin\",\"size\":10485760,"
                                + "\"partSize\":5242880,\"expiresIn\":\"PT10M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MULTIPART_INVALID"));

        verify(multipartStorage, never()).initiate(any(MultipartUploadRequest.class));
    }

    /** Local 分片上传并发耗尽时必须在调用 provider 前返回 429。 */
    @Test
    public void shouldRejectLocalMultipartPartBeforeProviderReadWhenPermitIsExhausted() throws Exception {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getMultipart().setMaxConcurrentUploads(1);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MultipartUploadService service = new MultipartUploadService(storage,
                (principal, key, operation) -> true, properties, clock);
        MultipartUploadSession session = service.initiate(() -> "alice", new MultipartUploadRequest(
                ObjectKey.of("uploads/limited.bin"), 10L * 1024L * 1024L, 10L * 1024L * 1024L,
                "application/octet-stream", CacheControl.privateNoCache(), Duration.ofMinutes(10L)));
        MockMvc limitedMvc = MockMvcBuilders.standaloneSetup(new MultipartUploadController(service))
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        WebTransferLimiter.Permit heldPermit = service.acquireLocalPartPermit();

        try {
            limitedMvc.perform(put("/files/multipart/" + session.uploadId() + "/parts/1")
                            .principal(() -> "alice").contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content("blocked"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "1"));
            assertTrue(service.listParts(() -> "alice", session.uploadId()).isEmpty());
        } finally {
            heldPermit.close();
        }
    }

    /** 过期会话清理必须删除 Local 临时状态并移除主体绑定。 */
    @Test
    public void shouldCleanupExpiredMultipartSessionState() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MultipartUploadService service = new MultipartUploadService(storage,
                (principal, key, operation) -> true, new FileStorageProperties(), clock);
        MultipartUploadSession session = service.initiate(() -> "alice", new MultipartUploadRequest(
                ObjectKey.of("uploads/expired.bin"), 10L * 1024L * 1024L, 10L * 1024L * 1024L,
                "application/octet-stream", CacheControl.privateNoCache(), Duration.ofSeconds(1L)));

        assertEquals(1, service.cleanupExpiredSessions(NOW.plusSeconds(2L)));
        try {
            service.listParts(() -> "alice", session.uploadId());
            org.junit.Assert.fail("expected cleaned multipart session to be unavailable");
        } catch (FileStorageException exception) {
            assertEquals(FileStorageErrorCode.MULTIPART_NOT_FOUND, exception.errorCode());
        }
    }

    /** 清理过期 Local 会话必须等待正在写入的分片完成，避免删除活跃临时目录。 */
    @Test
    public void shouldWaitForActiveLocalPartUploadBeforeCleaningExpiredSession() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MultipartUploadService service = new MultipartUploadService(storage,
                (principal, key, operation) -> true, new FileStorageProperties(), clock);
        MultipartUploadSession session = service.initiate(() -> "alice", new MultipartUploadRequest(
                ObjectKey.of("uploads/active-expired.bin"), 10L * 1024L * 1024L, 10L * 1024L * 1024L,
                "application/octet-stream", CacheControl.privateNoCache(), Duration.ofSeconds(1L)));
        BlockingMultipartInputStream content = new BlockingMultipartInputStream(10 * 1024 * 1024);
        AtomicReference<MultipartPart> uploaded = new AtomicReference<>();
        AtomicReference<Throwable> uploadFailure = new AtomicReference<>();
        AtomicReference<Integer> cleaned = new AtomicReference<>();
        CountDownLatch cleanupFinished = new CountDownLatch(1);
        Thread upload = new Thread(() -> {
            try {
                uploaded.set(service.uploadPart(() -> "alice", session.uploadId(), 1, content,
                        10L * 1024L * 1024L));
            } catch (Throwable throwable) {
                uploadFailure.set(throwable);
            }
        }, "multipart-active-part-upload-test");
        Thread cleanup = new Thread(() -> {
            cleaned.set(service.cleanupExpiredSessions(NOW.plusSeconds(2L)));
            cleanupFinished.countDown();
        }, "multipart-active-session-cleanup-test");

        upload.start();
        assertTrue(content.awaitReadStarted());
        cleanup.start();
        assertFalse(cleanupFinished.await(200L, TimeUnit.MILLISECONDS));

        content.releaseRead();
        upload.join(TimeUnit.SECONDS.toMillis(2L));
        cleanup.join(TimeUnit.SECONDS.toMillis(2L));
        assertFalse(upload.isAlive());
        assertFalse(cleanup.isAlive());
        assertTrue(uploadFailure.get() == null);
        assertTrue(uploaded.get() != null);
        assertEquals(Integer.valueOf(1), cleaned.get());
    }

    /** 无法解析的 multipart JSON 必须返回稳定参数错误。 */
    @Test
    public void shouldReturnStableErrorForMalformedMultipartJson() throws Exception {
        mockMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MULTIPART_INVALID"));
    }

    /** 不支持 multipart 的自定义 provider 不得阻断应用启动，并返回稳定错误。 */
    @Test
    public void shouldRejectMultipartForProviderWithoutMultipartCapability() throws Exception {
        FileStorage storageWithoutMultipart = mock(FileStorage.class);
        MockMvc unsupportedMvc = multipartMvc(storageWithoutMultipart,
                (principal, key, operation) -> true);

        unsupportedMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content(multipartInitiateJson("uploads/a.bin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MULTIPART_INVALID"));
    }

    /** 远端 multipart provider 的分片签发必须返回其原生预签名授权。 */
    @Test
    public void shouldReturnNativePresignedPartAuthorizationForRemoteProvider() throws Exception {
        MultipartFileStorage multipartStorage = multipartStorageMock();
        MultipartUploadSession session = new MultipartUploadSession("remote-upload", ObjectKey.of("uploads/a.bin"),
                10L * 1024L * 1024L, 10L * 1024L * 1024L, "application/octet-stream",
                CacheControl.privateNoCache(), 1, NOW.plus(Duration.ofMinutes(10L)), java.util.List.of());
        PresignedUpload upload = new PresignedUpload("https://provider.example/upload-part", "PUT",
                NOW.plus(Duration.ofMinutes(5L)), java.util.Map.of("Content-Type", "application/octet-stream"),
                java.util.Map.of());
        when(multipartStorage.initiate(any(MultipartUploadRequest.class))).thenReturn(session);
        when(multipartStorage.presignPart(any(), any())).thenReturn(upload);
        MockMvc remoteMvc = multipartMvc(multipartStorage, (principal, key, operation) -> true);

        remoteMvc.perform(post("/files/multipart").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content(multipartInitiateJson("uploads/a.bin")))
                .andExpect(status().isCreated());
        remoteMvc.perform(post("/files/multipart/remote-upload/parts/1/presign").principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://provider.example/upload-part"))
                .andExpect(jsonPath("$.method").value("PUT"));
    }

    /**
     * Local multipart 会话必须支持分片写入、查询、完成和重复完成。
     *
     * @throws Exception MVC 请求执行失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldCompleteLocalMultipartUploadIdempotently() throws Exception {
        String uploadId = initiateMultipart("alice", "uploads/large.bin");
        byte[] content = new byte[10 * 1024 * 1024];
        java.util.Arrays.fill(content, (byte) 'x');

        MvcResult partResult = mockMvc.perform(put("/files/multipart/" + uploadId + "/parts/1")
                        .principal(() -> "alice").contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(1))
                .andExpect(jsonPath("$.size").value(content.length)).andReturn();
        String etag = json(partResult).path("etag").asText();

        mockMvc.perform(get("/files/multipart/" + uploadId + "/parts").principal(() -> "alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].partNumber").value(1))
                .andExpect(jsonPath("$[0].etag").value(etag));

        String completion = "{\"parts\":[{\"partNumber\":1,\"size\":" + content.length
                + ",\"etag\":\"" + etag + "\"}],\"idempotencyKey\":\"complete-1\"}";
        mockMvc.perform(post("/files/multipart/" + uploadId + "/complete").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key.value").value("uploads/large.bin"))
                .andExpect(jsonPath("$.contentLength").value(content.length));
        mockMvc.perform(post("/files/multipart/" + uploadId + "/complete").principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentLength").value(content.length));
        assertEquals(content.length, storage.stat(ObjectKey.of("uploads/large.bin")).orElseThrow()
                .contentLength());
    }

    /**
     * multipart 会话不得被其他主体查询或写入。
     *
     * @throws Exception MVC 请求执行失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldRejectMultipartSessionAccessFromDifferentPrincipal() throws Exception {
        String uploadId = initiateMultipart("alice", "uploads/private.bin");

        mockMvc.perform(get("/files/multipart/" + uploadId + "/parts").principal(() -> "bob"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MULTIPART_NOT_FOUND"));
    }

    /**
     * 已取消 multipart 会话应拒绝后续分片请求，重复取消保持成功语义。
     *
     * @throws Exception MVC 请求执行失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void shouldAbortMultipartUploadIdempotently() throws Exception {
        String uploadId = initiateMultipart("alice", "uploads/cancelled.bin");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/files/multipart/" + uploadId).principal(() -> "alice"))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/files/multipart/" + uploadId).principal(() -> "alice"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/files/multipart/" + uploadId + "/parts").principal(() -> "alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MULTIPART_NOT_FOUND"));
    }

    /** 同一会话的并发完成请求必须只调用一次 provider complete。 */
    @Test
    public void shouldCompleteSameMultipartSessionOnlyOnceUnderConcurrentRequests() throws Exception {
        MultipartFileStorage multipartStorage = multipartStorageMock();
        MultipartUploadSession session = new MultipartUploadSession("concurrent-upload",
                ObjectKey.of("uploads/concurrent.bin"), 10L * 1024L * 1024L, 10L * 1024L * 1024L,
                "application/octet-stream", CacheControl.privateNoCache(), 1,
                NOW.plus(Duration.ofMinutes(10L)), java.util.List.of());
        MultipartPart part = new MultipartPart(1, 10L * 1024L * 1024L, "part-etag");
        ObjectMetadata metadata = new ObjectMetadata(session.key(), part.size(), session.contentType(),
                "object-etag", NOW, session.cacheControl());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch providerReleased = new CountDownLatch(1);
        when(multipartStorage.initiate(any(MultipartUploadRequest.class))).thenReturn(session);
        when(multipartStorage.complete(any(), any())).thenAnswer(invocation -> {
            providerStarted.countDown();
            providerReleased.await(2L, TimeUnit.SECONDS);
            return metadata;
        });
        MultipartUploadService service = new MultipartUploadService((FileStorage) multipartStorage,
                (principal, key, operation) -> true, new FileStorageProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        service.initiate(() -> "alice", new MultipartUploadRequest(session.key(), part.size(), part.size(),
                session.contentType(), session.cacheControl(), Duration.ofMinutes(10L)));
        AtomicReference<ObjectMetadata> first = new AtomicReference<>();
        AtomicReference<ObjectMetadata> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        MultipartCompletion completion = new MultipartCompletion(java.util.List.of(part));
        Thread firstRequest = completeInThread(service, session.uploadId(), completion, first, failure,
                "multipart-first-complete-test");
        Thread secondRequest = completeInThread(service, session.uploadId(), completion, second, failure,
                "multipart-second-complete-test");

        firstRequest.start();
        assertTrue(providerStarted.await(2L, TimeUnit.SECONDS));
        secondRequest.start();
        providerReleased.countDown();
        firstRequest.join(TimeUnit.SECONDS.toMillis(2L));
        secondRequest.join(TimeUnit.SECONDS.toMillis(2L));

        assertFalse(firstRequest.isAlive());
        assertFalse(secondRequest.isAlive());
        assertTrue(failure.get() == null);
        assertEquals(metadata, first.get());
        assertEquals(metadata, second.get());
        verify(multipartStorage).complete(session, completion);
    }

    /**
     * 直接上传也必须返回与预签名上传相同的对象结果。
     *
     * @throws Exception MVC 请求执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldReturnMetadataAndRealKeyPreviewUrlForDirectUpload() throws Exception {
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(true, (principal, key, operation) -> true),
                Clock.fixed(NOW, ZoneOffset.UTC), stableAccess);
        MockMvc directMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();

        directMvc.perform(put("/files/upload/1/1/1/1.png")
                        .contentType(MediaType.IMAGE_PNG).content("image"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/files/read/key/preview/1/1/1/1.png"))
                .andExpect(jsonPath("$.key.value").value("1/1/1/1.png"))
                .andExpect(jsonPath("$.contentLength").value(5))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.previewUrl").value("/files/read/key/preview/1/1/1/1.png"));
    }

    /**
     * 自定义路径前缀的响应应保留 key 层级并编码路径字符。
     *
     * @throws Exception MVC 请求执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldUseConfiguredPrefixAndEncodedKeyForPreviewUrl() throws Exception {
        String pathPrefix = "/assets";
        LocalFileStorageAdapter customStorage = new LocalFileStorageAdapter(
                temporaryFolder.newFolder("custom-storage").toPath(), SIGNING_KEY, 1024L,
                java.util.Set.of(), Duration.ofHours(1L), Clock.fixed(NOW, ZoneOffset.UTC), pathPrefix);
        FileStorageWebController controller = new FileStorageWebController(customStorage,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, null, new WebTransferConfiguration(128 * 1024, 128, 16, 0, null, pathPrefix));
        MockMvc customMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        ObjectKey key = ObjectKey.of("目录/hello world.png");
        PresignedUpload upload = customStorage.presignUpload(new PresignUploadRequest(
                key, Duration.ofMinutes(10L), "image/png", 20L));
        String previewUrl = "/assets/read/key/preview/%E7%9B%AE%E5%BD%95/hello%20world.png";

        customMvc.perform(put(upload.url()).contentType(MediaType.IMAGE_PNG).content("image"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", previewUrl))
                .andExpect(jsonPath("$.key.value").value(key.value()))
                .andExpect(jsonPath("$.previewUrl").value(previewUrl));
    }

    /**
     * 启用后历史直读地址必须复用既有对象读取、缓存和 Range 处理链路。
     *
     * @throws Exception MVC 请求执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldReadUnicodeObjectThroughConfiguredLegacyRoute() throws Exception {
        ObjectKey key = ObjectKey.of("archives/目录/hello world.gltf");
        write(key, "hello", "model/gltf+json", new CacheControl("public, max-age=60"));
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix("rsm");
        web.setReadPath("content");
        web.setStableReadPath("content");
        web.setLegacyReadEnabled(true);
        web.setLegacyReadPath("/");
        FileStorageWebRoutes routes = FileStorageWebRoutes.from(web);
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, null, new WebTransferConfiguration(128 * 1024, 128, 16, 0, null,
                routes, true));
        MockMvc legacyMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        String encodedPath = "/rsm/archives/%E7%9B%AE%E5%BD%95/hello%20world.gltf";

        MvcResult initial = legacyMvc.perform(get(encodedPath))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult response = legacyMvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(content().string("hello")).andExpect(header().exists("ETag")).andReturn();
        legacyMvc.perform(get(encodedPath).header("If-None-Match", response.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
        MvcResult range = legacyMvc.perform(get(encodedPath).header("Range", "bytes=1-3"))
                .andExpect(request().asyncStarted()).andReturn();
        legacyMvc.perform(asyncDispatch(range)).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-3/5"))
                .andExpect(content().string("ell"));
    }

    /**
     * 未显式启用历史直读地址时，通用 catch-all 路由不得被注册。
     *
     * @throws Exception MVC 请求执行失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Test
    public void shouldNotRegisterLegacyRouteWhenDisabled() throws Exception {
        FileStorageProperties.Web web = new FileStorageProperties.Web();
        web.setPathPrefix("rsm");
        web.setReadPath("content");
        FileStorageWebRoutes routes = FileStorageWebRoutes.from(web);
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, null, new WebTransferConfiguration(128 * 1024, 128, 16, 0, null,
                routes, true));
        MockMvc disabledMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();

        disabledMvc.perform(get("/rsm/documents/readme.txt")).andExpect(status().isNotFound());
    }

    /** 无签名预览按匿名读取契约启动响应。 */
    @Test
    public void shouldReadUnsignedPreviewByDefault() throws Exception {
        mockMvc.perform(get("/files/preview/documents/readme.txt"))
                .andExpect(request().asyncStarted());
    }


    /** 启用宿主直接访问后，真实对象 key 读取地址应可用。 */
    @Test
    public void shouldReadThroughRealObjectKeyAddress() throws Exception {
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(true, (principal, key, operation) -> true),
                Clock.fixed(NOW, ZoneOffset.UTC), stableAccess);
        MockMvc directMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();

        MvcResult started = directMvc.perform(get("/files/read/key/documents/readme.txt"))
                .andExpect(request().asyncStarted()).andReturn();
        directMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(content().string("hello"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    /** 真实 key 读取不应调用宿主直接访问授权。 */
    @Test
    public void shouldReadRealObjectKeyWhenDirectAccessIsDisabled() throws Exception {
        MvcResult started = mockMvc.perform(get("/files/read/key/documents/readme.txt"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    /**
     * 附件下载和内联预览应使用各自端点与处置模式。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldDownloadAndPreviewThroughSignedUrls() throws Exception {
        PresignedDownload download = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.ATTACHMENT));
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));
        storage.verifyPresignedDownload(downloadRequest("documents/readme.txt",
                PresignDownloadRequest.ResponseDisposition.ATTACHMENT), download.expiresAt(),
                queryParameter(download.url(), "signature"), NOW);

        assertTrue(download.url().contains("/files/download/documents/readme.txt"));
        assertTrue(preview.url().contains("/files/preview/documents/readme.txt"));
        MvcResult downloadStarted = mockMvc.perform(get(download.url()))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(downloadStarted)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string("hello"));
        MvcResult previewStarted = mockMvc.perform(get(preview.url()))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(previewStarted)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().string("hello"));
    }

    /**
     * 中文 key 的签名 URL 必须能经 URL 编码后读取对象。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldReadUnicodeKeyThroughEncodedPresignedUrl() throws Exception {
        ObjectKey key = ObjectKey.of("用户资料/头像.png");
        write(key, "avatar", "image/png", CacheControl.privateNoCache());
        PresignedDownload download = storage.presignDownload(downloadRequest(key.value(),
                PresignDownloadRequest.ResponseDisposition.INLINE));

        MvcResult started = mockMvc.perform(get(download.url()))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(content().string("avatar"));
    }

    /**
     * 已签名的最大上传大小不能被请求体绕过。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRejectContentExceedingPresignedMaximum() throws Exception {
        PresignedUpload upload = storage.presignUpload(new PresignUploadRequest(
                ObjectKey.of("uploads/limited.txt"), Duration.ofMinutes(10L), "text/plain", 3L));

        mockMvc.perform(put(upload.url()).contentType(MediaType.TEXT_PLAIN).content("four"))
                .andExpect(status().isPayloadTooLarge());
    }

    /**
     * ETag 条件请求和单 Range 请求应返回标准缓存状态。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldSupportEtagNotModifiedAndSingleRange() throws Exception {
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));
        MvcResult initialStarted = mockMvc.perform(get(preview.url()))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult response = mockMvc.perform(asyncDispatch(initialStarted))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("Last-Modified"))
                .andExpect(header().exists("Expires"))
                .andReturn();
        String etag = response.getResponse().getHeader("ETag");

        mockMvc.perform(get(preview.url()).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        MvcResult rangeStarted = mockMvc.perform(get(preview.url()).header("Range", "bytes=1-3"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(rangeStarted)).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-3/5"))
                .andExpect(content().string("ell"));
        mockMvc.perform(get(preview.url()).header("Range", "bytes=20-30"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */5"));
    }

    /** Tomcat 支持时完整本地对象应注册 sendfile 而非启动异步流式复制。 */
    @Test
    public void shouldRegisterTomcatSendfileForCompleteLocalObject() throws Exception {
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));

        MvcResult result = mockMvc.perform(get(preview.url())
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncNotStarted()).andReturn();

        assertTrue(result.getRequest().getAttribute(TomcatSendfileTransfer.FILENAME) != null);
        assertEquals(0L, result.getRequest().getAttribute(TomcatSendfileTransfer.START));
        assertEquals(5L, result.getRequest().getAttribute(TomcatSendfileTransfer.END));
    }

    /** 单个合法 Range 应注册对应的 Tomcat sendfile 字节区间。 */
    @Test
    public void shouldRegisterTomcatSendfileForLocalSingleRange() throws Exception {
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));

        MvcResult result = mockMvc.perform(get(preview.url()).header("Range", "bytes=1-3")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isPartialContent()).andExpect(request().asyncNotStarted())
                .andExpect(header().string("Content-Range", "bytes 1-3/5")).andReturn();

        assertEquals(1L, result.getRequest().getAttribute(TomcatSendfileTransfer.START));
        assertEquals(4L, result.getRequest().getAttribute(TomcatSendfileTransfer.END));
    }

    /** sendfile 响应应在 MVC 完成回调前持有同一 key 许可，并在回调后释放。 */
    @Test
    public void shouldReleaseSameResolverKeyPermitAfterSendfileCompletion() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), clock, stableAccess, null,
                webTransferConfiguration(2, 16, request -> request.getHeader("X-Tenant")));
        MockMvc keyLimitedMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));

        MvcResult first = keyLimitedMvc.perform(get(preview.url()).header("X-Tenant", "tenant-a")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncNotStarted()).andReturn();
        keyLimitedMvc.perform(get(preview.url()).header("X-Tenant", "tenant-a")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        FileStorageWebMvcConfiguration.releaseReadPermit(first.getRequest());
        keyLimitedMvc.perform(get(preview.url()).header("X-Tenant", "tenant-a")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncNotStarted());
    }

    /** Tomcat 响应会压缩时必须保留原有有界异步流式路径。 */
    @Test
    public void shouldFallBackToStreamingWhenTomcatRequestAcceptsCompression() throws Exception {
        PresignedDownload preview = storage.presignDownload(downloadRequest(
                "documents/readme.txt", PresignDownloadRequest.ResponseDisposition.INLINE));

        mockMvc.perform(get(preview.url()).header("Accept-Encoding", "gzip")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncStarted());
    }

    /** provider 确认的 PNG 类型在 gzip 请求下应使用默认开启的 sendfile 优化。 */
    @Test
    public void shouldRegisterTomcatSendfileForBinaryMetadataWhenRequestAcceptsCompression() throws Exception {
        ObjectKey key = ObjectKey.of("documents/preview.png");
        write(key, "image", "image/png", new CacheControl("public, max-age=60"));
        String payload = stableAccess.identifier(key).substring("v1.".length());

        MvcResult result = mockMvc.perform(get("/files/read/v1/" + payload)
                        .header("Accept-Encoding", "gzip")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncNotStarted()).andReturn();

        assertTrue(result.getRequest().getAttribute(TomcatSendfileTransfer.FILENAME) != null);
    }

    /** 宿主关闭二进制 sendfile 优化后，gzip 请求必须回退有界流式路径。 */
    @Test
    public void shouldFallBackToStreamingWhenBinarySendfileOptimizationIsDisabled() throws Exception {
        ObjectKey key = ObjectKey.of("documents/preview.png");
        write(key, "image", "image/png", new CacheControl("public, max-age=60"));
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, null, new WebTransferConfiguration(128 * 1024, 128, 16, 0, null,
                "/files", false));
        MockMvc disabledMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        String payload = stableAccess.identifier(key).substring("v1.".length());

        disabledMvc.perform(get("/files/read/v1/" + payload).header("Accept-Encoding", "gzip")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncStarted());
    }

    /** 非法 provider MIME 不能因响应头降级为二进制类型而错误启用 sendfile。 */
    @Test
    public void shouldStreamWhenRawProviderMetadataIsInvalidEvenIfResponseHeaderFallsBack() throws Exception {
        ObjectKey key = ObjectKey.of("documents/preview.png");
        write(key, "image", "not-a-media-type", new CacheControl("public, max-age=60"));
        String payload = stableAccess.identifier(key).substring("v1.".length());

        mockMvc.perform(get("/files/read/v1/" + payload).header("Accept-Encoding", "gzip")
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    /** 已校验的远程缓存热读应注册 Tomcat sendfile，而不是重新打开远程对象流。 */
    @Test
    public void shouldRegisterTomcatSendfileForVerifiedRemoteCacheHit() throws Exception {
        InMemoryFileStorage provider = new InMemoryFileStorage("remote");
        ProviderFileReadCache cache = new ProviderFileReadCache(provider, "documents",
                temporaryFolder.newFolder("cache").toPath(), true, 1024L);
        try (DownloadObject object = cache.get(ObjectKey.of("reports/remote.txt")).orElseThrow()) {
            object.stream().readAllBytes();
        }
        cache.awaitIdle(2L, java.util.concurrent.TimeUnit.SECONDS);
        FileStorageWebController controller = new FileStorageWebController(provider,
                new FileStorageDirectAccessAuthorizer(false, null), Clock.fixed(NOW, ZoneOffset.UTC),
                stableAccess, cache);
        MockMvc cacheMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        String payload = stableAccess.identifier(ObjectKey.of("reports/remote.txt")).substring("v1.".length());

        MvcResult result = cacheMvc.perform(get("/files/read/v1/" + payload)
                        .requestAttr(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE))
                .andExpect(status().isOk()).andExpect(request().asyncNotStarted()).andReturn();

        assertTrue(result.getRequest().getAttribute(TomcatSendfileTransfer.FILENAME) != null);
        assertEquals(0L, result.getRequest().getAttribute(TomcatSendfileTransfer.START));
        assertEquals(6L, result.getRequest().getAttribute(TomcatSendfileTransfer.END));
        cache.close();
    }

    /** 稳定读取地址不带认证主体也可预览、下载和按范围读取私有对象。 */
    @Test
    public void shouldReadPrivateObjectThroughStableIdWithoutPrincipal() throws Exception {
        String identifier = stableAccess.identifier(ObjectKey.of("documents/readme.txt"));
        String payload = identifier.substring("v1.".length());

        MvcResult readStarted = mockMvc.perform(get("/files/read/v1/" + payload))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(readStarted)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().string("hello"));
        MvcResult previewStarted = mockMvc.perform(get("/files/read/v1/preview/" + payload)
                        .header("Range", "bytes=1-3"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(previewStarted)).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-3/5"))
                .andExpect(content().string("ell"));
        MvcResult downloadStarted = mockMvc.perform(get("/files/read/v1/download/" + payload))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(downloadStarted)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    /** 稳定读取地址应按当前 ETag 返回 304，且篡改 ID 不得触发对象读取。 */
    @Test
    public void shouldSupportNotModifiedAndRejectTamperedStableId() throws Exception {
        String identifier = stableAccess.identifier(ObjectKey.of("documents/readme.txt"));
        String payload = identifier.substring("v1.".length());
        MvcResult initialStarted = mockMvc.perform(get("/files/read/v1/" + payload))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult initial = mockMvc.perform(asyncDispatch(initialStarted)).andExpect(status().isOk())
                .andReturn();
        String etag = initial.getResponse().getHeader("ETag");

        mockMvc.perform(get("/files/read/v1/" + payload).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        char replacement = identifier.endsWith("A") ? 'B' : 'A';
        mockMvc.perform(get("/files/read/v1/" + payload.substring(0, payload.length() - 1)
                        + replacement))
                .andExpect(status().isBadRequest());
    }

    /** 稳定读取仅使用通用存储契约，不调用宿主直接访问授权策略。 */
    @Test
    public void shouldReadThroughProviderNeutralStorageWithoutHostAuthorization() throws Exception {
        InMemoryFileStorage provider = new InMemoryFileStorage("remote");
        FileStorageAccessPolicy rejectingPolicy = (principal, key, operation) -> {
            throw new AssertionError("stable read must not call host authorization");
        };
        StableFileAccessService access = new StableFileAccessService("documents",
                "stable-file-id-test-key-material-32b".getBytes(StandardCharsets.UTF_8));
        FileStorageWebController controller = new FileStorageWebController(provider,
                new FileStorageDirectAccessAuthorizer(true, rejectingPolicy), Clock.fixed(NOW, ZoneOffset.UTC),
                access);
        MockMvc providerMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        String payload = access.identifier(ObjectKey.of("reports/remote.txt")).substring("v1.".length());

        MvcResult started = providerMvc.perform(get("/files/read/v1/" + payload))
                .andExpect(request().asyncStarted()).andReturn();

        providerMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(content().string("remote"));
    }

    /** 同一宿主 key 的流式读取在完成前应返回 429，完成后立即释放许可。 */
    @Test
    public void shouldLimitSameResolverKeyUntilStreamingReadCompletes() throws Exception {
        BlockingReadStorage blockingStorage = new BlockingReadStorage();
        MockMvc keyLimitedMvc = blockingReadMvc(blockingStorage, request -> request.getHeader("X-Tenant"), 2);
        String path = stableReadPath();

        MvcResult first = keyLimitedMvc.perform(get(path).header("X-Tenant", "tenant-a"))
                .andExpect(request().asyncStarted()).andReturn();
        assertTrue(blockingStorage.awaitFirstRead());
        keyLimitedMvc.perform(get(path).header("X-Tenant", "tenant-a"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        blockingStorage.releaseFirstRead();
        keyLimitedMvc.perform(asyncDispatch(first)).andExpect(status().isOk())
                .andExpect(content().string("remote"));
        MvcResult afterRelease = keyLimitedMvc.perform(get(path).header("X-Tenant", "tenant-a"))
                .andExpect(request().asyncStarted()).andReturn();
        keyLimitedMvc.perform(asyncDispatch(afterRelease)).andExpect(status().isOk());
    }

    /** resolver 返回 null 时应忽略每 key 配额并保持全局读取上限。 */
    @Test
    public void shouldUseGlobalReadLimitWhenResolverReturnsNull() throws Exception {
        assertResolverFallsBackToGlobalReadLimit(request -> null);
    }

    /** resolver 返回空白 key 时应忽略每 key 配额并保持全局读取上限。 */
    @Test
    public void shouldUseGlobalReadLimitWhenResolverReturnsBlank() throws Exception {
        assertResolverFallsBackToGlobalReadLimit(request -> " ");
    }

    /** resolver 抛出运行时异常时应忽略每 key 配额并保持全局读取上限。 */
    @Test
    public void shouldUseGlobalReadLimitWhenResolverThrows() throws Exception {
        assertResolverFallsBackToGlobalReadLimit(request -> {
            throw new IllegalStateException("resolver failure");
        });
    }

    /** 同一宿主 key 的上传达到上限时应返回 429，且不得进入 provider 写入。 */
    @Test
    public void shouldRejectSameResolverKeyUploadBeforeProviderWrite() throws Exception {
        BlockingUploadStorage blockingStorage = new BlockingUploadStorage();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FileStorageWebController controller = new FileStorageWebController(blockingStorage,
                new FileStorageDirectAccessAuthorizer(true, (principal, key, operation) -> true), clock,
                stableAccess, null, webTransferConfiguration(2, 2,
                request -> request.getHeader("X-Tenant")));
        MockMvc keyLimitedMvc = FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread firstRequest = new Thread(() -> runBlockingUpload(keyLimitedMvc, firstFailure),
                "file-storage-upload-test");

        firstRequest.start();
        assertTrue(blockingStorage.awaitFirstWrite());
        keyLimitedMvc.perform(put("/files/upload/uploads/second.txt").header("X-Tenant", "tenant-a")
                        .contentType(MediaType.TEXT_PLAIN).content("second"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));
        assertEquals(1, blockingStorage.writeCount());

        blockingStorage.releaseFirstWrite();
        firstRequest.join(TimeUnit.SECONDS.toMillis(2L));
        assertTrue(!firstRequest.isAlive());
        assertTrue(firstFailure.get() == null);
    }

    /** 验证 resolver 降级场景仍使用全局读取许可。 */
    private void assertResolverFallsBackToGlobalReadLimit(WebTransferRateLimitKeyResolver resolver)
            throws Exception {
        BlockingReadStorage blockingStorage = new BlockingReadStorage();
        MockMvc keyLimitedMvc = blockingReadMvc(blockingStorage, resolver, 1);
        String path = stableReadPath();
        MvcResult first = keyLimitedMvc.perform(get(path).header("X-Tenant", "tenant-a"))
                .andExpect(request().asyncStarted()).andReturn();
        assertTrue(blockingStorage.awaitFirstRead());

        keyLimitedMvc.perform(get(path).header("X-Tenant", "tenant-b"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));
        blockingStorage.releaseFirstRead();
        keyLimitedMvc.perform(asyncDispatch(first)).andExpect(status().isOk());
    }

    /**
     * 创建测试 multipart 会话并返回会话标识。
     *
     * @param principalName 发起会话的主体名称
     * @param key 待上传对象 key
     * @return provider 返回的会话标识
     * @throws Exception MVC 请求执行失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private String initiateMultipart(String principalName, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/files/multipart").principal(() -> principalName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multipartInitiateJson(key)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).path("uploadId").asText();
    }

    /** 创建符合默认 multipart 限制的请求 JSON。 */
    private String multipartInitiateJson(String key) {
        return "{\"key\":\"" + key + "\",\"size\":10485760,\"partSize\":10485760,"
                + "\"contentType\":\"application/octet-stream\",\"expiresIn\":\"PT10M\"}";
    }

    /** 创建同时实现文件存储与 multipart 能力的远端 provider 测试替身。 */
    private MultipartFileStorage multipartStorageMock() {
        return (MultipartFileStorage) mock(FileStorage.class, withSettings()
                .extraInterfaces(MultipartFileStorage.class));
    }

    /** 创建只暴露 multipart 控制器的测试客户端。 */
    private MockMvc multipartMvc(MultipartFileStorage multipartStorage, FileStorageAccessPolicy accessPolicy) {
        return multipartMvc((FileStorage) multipartStorage, accessPolicy);
    }

    /** 创建只暴露 multipart 控制器的测试客户端。 */
    private MockMvc multipartMvc(FileStorage storageForTest, FileStorageAccessPolicy accessPolicy) {
        MultipartUploadController controller = new MultipartUploadController(new MultipartUploadService(
                storageForTest, accessPolicy, new FileStorageProperties(), Clock.fixed(NOW,
                ZoneOffset.UTC)));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
    }

    /**
     * 读取 MVC JSON 响应。
     *
     * @param result 已完成的 MVC 请求
     * @return JSON 根节点
     * @throws Exception 响应解析失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** 创建使用可控阻塞读取 provider 的每 key 限流 MVC 客户端。 */
    private MockMvc blockingReadMvc(BlockingReadStorage blockingStorage,
                                    WebTransferRateLimitKeyResolver resolver, int maxConcurrentReads) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FileStorageWebController controller = new FileStorageWebController(blockingStorage,
                new FileStorageDirectAccessAuthorizer(false, null), clock, stableAccess, null,
                webTransferConfiguration(maxConcurrentReads, 16, resolver));
        return FileStorageWebMvcTestSupport.standaloneSetup(controller)
                .setControllerAdvice(new FileStorageExceptionHandler()).build();
    }

    /** 返回阻塞读取 provider 对象的稳定 ID 路径。 */
    private String stableReadPath() {
        String identifier = stableAccess.identifier(BlockingReadStorage.KEY);
        return "/files/read/v1/" + identifier.substring("v1.".length());
    }

    /** 创建启用每 key 限制的 Web 传输配置。 */
    private WebTransferConfiguration webTransferConfiguration(int maxConcurrentReads,
                                                              int maxConcurrentUploads,
                                                              WebTransferRateLimitKeyResolver resolver) {
        return new WebTransferConfiguration(128 * 1024, maxConcurrentReads, maxConcurrentUploads, 1,
                resolver);
    }

    /** 在独立线程中发起会阻塞 provider 写入的上传请求。 */
    private void runBlockingUpload(MockMvc keyLimitedMvc, AtomicReference<Throwable> failure) {
        try {
            keyLimitedMvc.perform(put("/files/upload/uploads/first.txt").header("X-Tenant", "tenant-a")
                            .contentType(MediaType.TEXT_PLAIN).content("first"))
                    .andExpect(status().isCreated());
        } catch (Exception exception) {
            failure.set(exception);
        }
    }

    /** 在独立线程中提交同一个 multipart 完成请求。 */
    private Thread completeInThread(MultipartUploadService service, String uploadId,
                                    MultipartCompletion completion, AtomicReference<ObjectMetadata> result,
                                    AtomicReference<Throwable> failure, String name) {
        return new Thread(() -> {
            try {
                result.set(service.complete(() -> "alice", uploadId, completion));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, name);
    }

    /**
     * 创建预签名下载请求。
     *
     * @param key 对象 key
     * @param disposition 响应处置模式
     * @return 已配置下载请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private PresignDownloadRequest downloadRequest(String key,
                                                   PresignDownloadRequest.ResponseDisposition disposition) {
        return new PresignDownloadRequest(ObjectKey.of(key), Duration.ofMinutes(10L), disposition,
                new CacheControl("public, max-age=60"));
    }

    /**
     * 写入测试对象。
     *
     * @param key 对象 key
     * @param value 对象内容
     * @param contentType 内容类型
     * @param cacheControl 缓存策略
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void write(ObjectKey key, String value, String contentType, CacheControl cacheControl) {
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        storage.put(new UploadRequest(key, new ByteArrayInputStream(content), content.length,
                contentType, cacheControl));
    }

    /**
     * 读取存储对象的 UTF-8 内容。
     *
     * @param key 对象 key
     * @return UTF-8 内容
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String read(ObjectKey key) throws Exception {
        try (var download = storage.get(key).orElseThrow()) {
            return new String(download.stream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 读取签名 URL 查询参数。
     *
     * @param url 签名 URL
     * @param name 参数名
     * @return 参数值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String queryParameter(String url, String name) {
        for (String part : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] values = part.split("=", 2);
            if (values.length == 2 && values[0].equals(name)) {
                return URLDecoder.decode(values[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("missing query parameter");
    }

    /** 仅实现读取所需能力的远程 provider 测试替身。 */
    private static final class InMemoryFileStorage implements FileStorage {
        /** 对象内容。 */
        private final byte[] content;
        /** 对象元数据。 */
        private final ObjectMetadata metadata;

        /** 创建固定的远程对象。 */
        private InMemoryFileStorage(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
            metadata = new ObjectMetadata(ObjectKey.of("reports/remote.txt"), this.content.length,
                    "text/plain", "remote-etag", NOW, CacheControl.privateNoCache());
        }

        /** 读取匹配 key 的对象流。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            return metadata.key().equals(key) ? Optional.of(new DownloadObject(
                    new ByteArrayInputStream(content), metadata)) : Optional.empty();
        }

        /** 返回匹配 key 的对象元数据。 */
        @Override
        public Optional<ObjectMetadata> stat(ObjectKey key) {
            return metadata.key().equals(key) ? Optional.of(metadata) : Optional.empty();
        }

        /** 不支持读取测试之外的写入能力。 */
        @Override
        public ObjectMetadata put(UploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 判断对象是否存在。 */
        @Override
        public boolean exists(ObjectKey key) {
            return stat(key).isPresent();
        }

        /** 不支持删除。 */
        @Override
        public boolean delete(ObjectKey key) {
            throw new UnsupportedOperationException();
        }

        /** 枚举唯一对象。 */
        @Override
        public Stream<ObjectMetadata> list() {
            return Stream.of(metadata);
        }

        /** 不支持预签名上传。 */
        @Override
        public PresignedUpload presignUpload(PresignUploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 不支持预签名下载。 */
        @Override
        public PresignedDownload presignDownload(PresignDownloadRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    /** 在首次写入时阻塞的 provider 测试替身，用于验证上传许可生命周期。 */
    private static final class BlockingUploadStorage implements FileStorage {
        /** 首次写入已进入 provider 的同步信号。 */
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        /** 放行首次写入的同步信号。 */
        private final CountDownLatch firstWriteReleased = new CountDownLatch(1);
        /** provider 写入调用次数。 */
        private final AtomicInteger writes = new AtomicInteger();

        /** 等待首次写入进入 provider。 */
        private boolean awaitFirstWrite() throws InterruptedException {
            return firstWriteStarted.await(2L, TimeUnit.SECONDS);
        }

        /** 放行被阻塞的首次写入。 */
        private void releaseFirstWrite() {
            firstWriteReleased.countDown();
        }

        /** 返回 provider 写入调用次数。 */
        private int writeCount() {
            return writes.get();
        }

        /** 此测试替身不支持读取。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            return Optional.empty();
        }

        /** 此测试替身不支持元数据查询。 */
        @Override
        public Optional<ObjectMetadata> stat(ObjectKey key) {
            return Optional.empty();
        }

        /** 阻塞首次写入，以便并发请求验证限流行为。 */
        @Override
        public ObjectMetadata put(UploadRequest request) {
            writes.incrementAndGet();
            firstWriteStarted.countDown();
            try {
                if (!firstWriteReleased.await(2L, TimeUnit.SECONDS)) {
                    throw new AssertionError("first upload was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("first upload was interrupted", exception);
            }
            return new ObjectMetadata(request.key(), request.contentLength(), request.contentType(),
                    "upload-etag", NOW, request.cacheControl());
        }

        /** 此测试替身不保存对象。 */
        @Override
        public boolean exists(ObjectKey key) {
            return false;
        }

        /** 此测试替身不支持删除。 */
        @Override
        public boolean delete(ObjectKey key) {
            return false;
        }

        /** 此测试替身不支持列表查询。 */
        @Override
        public Stream<ObjectMetadata> list() {
            return Stream.empty();
        }

        /** 此测试替身不支持预签名上传。 */
        @Override
        public PresignedUpload presignUpload(PresignUploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 此测试替身不支持预签名下载。 */
        @Override
        public PresignedDownload presignDownload(PresignDownloadRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    /** 首次读取时阻塞的固定长度分片流，用于协调上传与清理测试。 */
    private static final class BlockingMultipartInputStream extends java.io.InputStream {
        /** 首次读取已开始的同步信号。 */
        private final CountDownLatch readStarted = new CountDownLatch(1);
        /** 放行分片读取的同步信号。 */
        private final CountDownLatch readReleased = new CountDownLatch(1);
        /** 剩余可读取字节数。 */
        private long remaining;
        /** 是否已经等待首次读取放行。 */
        private boolean hasWaited;

        /** 创建指定长度的阻塞分片流。 */
        private BlockingMultipartInputStream(long contentLength) {
            remaining = contentLength;
        }

        /** 等待 provider 开始读取分片数据。 */
        private boolean awaitReadStarted() throws InterruptedException {
            return readStarted.await(2L, TimeUnit.SECONDS);
        }

        /** 允许 provider 继续读取分片数据。 */
        private void releaseRead() {
            readReleased.countDown();
        }

        /** 读取一个填充字节。 */
        @Override
        public int read() throws java.io.IOException {
            byte[] buffer = new byte[1];
            return read(buffer, 0, 1) == -1 ? -1 : buffer[0] & 0xff;
        }

        /** 在首个批量读取时阻塞，再返回固定内容。 */
        @Override
        public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            if (remaining == 0L) {
                return -1;
            }
            waitForReadRelease();
            int count = (int) Math.min(remaining, length);
            java.util.Arrays.fill(buffer, offset, offset + count, (byte) 'a');
            remaining -= count;
            return count;
        }

        /** 等待测试线程允许首次读取继续。 */
        private void waitForReadRelease() throws java.io.IOException {
            if (!hasWaited) {
                hasWaited = true;
                readStarted.countDown();
                try {
                    if (!readReleased.await(2L, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("multipart read was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("multipart read was interrupted", exception);
                }
            }
        }
    }

    /** 在首个流读取时阻塞的 provider 测试替身，用于验证读取许可生命周期。 */
    private static final class BlockingReadStorage implements FileStorage {
        /** 被读取的固定对象 key。 */
        private static final ObjectKey KEY = ObjectKey.of("reports/remote.txt");
        /** 固定对象内容。 */
        private static final byte[] CONTENT = "remote".getBytes(StandardCharsets.UTF_8);
        /** 固定对象元数据。 */
        private static final ObjectMetadata METADATA = new ObjectMetadata(KEY, CONTENT.length,
                "text/plain", "blocking-etag", NOW, CacheControl.privateNoCache());
        /** 首次读取已进入流复制的同步信号。 */
        private final CountDownLatch firstReadStarted = new CountDownLatch(1);
        /** 放行首次读取的同步信号。 */
        private final CountDownLatch firstReadReleased = new CountDownLatch(1);

        /** 等待首个响应开始读取对象流。 */
        private boolean awaitFirstRead() throws InterruptedException {
            return firstReadStarted.await(2L, TimeUnit.SECONDS);
        }

        /** 放行首个响应读取对象流。 */
        private void releaseFirstRead() {
            firstReadReleased.countDown();
        }

        /** 返回会在首个字节读取时阻塞的对象流。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            if (KEY.equals(key)) {
                return Optional.of(new DownloadObject(new BlockingInputStream(), METADATA));
            }
            return Optional.empty();
        }

        /** 返回固定对象元数据。 */
        @Override
        public Optional<ObjectMetadata> stat(ObjectKey key) {
            if (KEY.equals(key)) {
                return Optional.of(METADATA);
            }
            return Optional.empty();
        }

        /** 此测试替身不支持写入。 */
        @Override
        public ObjectMetadata put(UploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 返回固定对象是否存在。 */
        @Override
        public boolean exists(ObjectKey key) {
            return KEY.equals(key);
        }

        /** 此测试替身不支持删除。 */
        @Override
        public boolean delete(ObjectKey key) {
            return false;
        }

        /** 枚举固定对象。 */
        @Override
        public Stream<ObjectMetadata> list() {
            return Stream.of(METADATA);
        }

        /** 此测试替身不支持预签名上传。 */
        @Override
        public PresignedUpload presignUpload(PresignUploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 此测试替身不支持预签名下载。 */
        @Override
        public PresignedDownload presignDownload(PresignDownloadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 仅在首次读取时阻塞的输入流。 */
        private final class BlockingInputStream extends ByteArrayInputStream {
            /** 是否已等待首次读取放行。 */
            private boolean hasWaited;

            /** 创建固定内容输入流。 */
            private BlockingInputStream() {
                super(CONTENT);
            }

            /** 等待测试线程放行首个读取后返回字节。 */
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                waitForFirstReadRelease();
                return super.read(buffer, offset, length);
            }

            /** 等待首个流复制进入 provider 后再继续读取。 */
            private void waitForFirstReadRelease() {
                if (!hasWaited) {
                    hasWaited = true;
                    firstReadStarted.countDown();
                    try {
                        if (!firstReadReleased.await(2L, TimeUnit.SECONDS)) {
                            throw new AssertionError("first read was not released");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("first read was interrupted", exception);
                    }
                }
            }
        }
    }
}
