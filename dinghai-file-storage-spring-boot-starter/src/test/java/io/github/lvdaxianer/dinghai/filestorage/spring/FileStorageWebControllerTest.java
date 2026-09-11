package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        FileStorageWebController controller = new FileStorageWebController(storage,
                new FileStorageDirectAccessAuthorizer(false, null), clock);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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
                .andExpect(status().isNoContent());

        assertEquals("new content", read(ObjectKey.of("uploads/new.txt")));
    }

    /**
     * 无签名的预览请求必须被默认策略拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Test
    public void shouldRejectUnsignedPreviewByDefault() throws Exception {
        mockMvc.perform(get("/files/preview/documents/readme.txt"))
                .andExpect(status().isForbidden());
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
}
