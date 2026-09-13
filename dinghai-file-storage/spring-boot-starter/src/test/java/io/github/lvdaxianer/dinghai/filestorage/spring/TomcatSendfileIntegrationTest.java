package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 验证内嵌 Tomcat 可以接收控制器注册的 sendfile 属性并传输本地文件。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
public class TomcatSendfileIntegrationTest {
    /** 稳定 ID 的版本前缀长度。 */
    private static final int STABLE_ID_VERSION_PREFIX_LENGTH = 3;
    /** 当前运行的应用上下文。 */
    private ConfigurableApplicationContext context;
    /** 每个请求由过滤器记录的 sendfile 参数。 */
    private static final AtomicReference<SendfileAttributes> SENDFILE = new AtomicReference<>();

    /** 关闭真实嵌入式 Web 服务器。 */
    @After
    public void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 完整对象和单 Range 请求应在真实 Tomcat 上注册 sendfile 并返回正确字节。
     *
     * @throws Exception 创建临时目录、启动服务器或请求文件时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    @Test
    public void shouldTransferCompleteObjectAndRangeThroughEmbeddedTomcatSendfile() throws Exception {
        Path root = Files.createTempDirectory("dinghai-sendfile-");
        context = start(root);
        ObjectKey key = ObjectKey.of("reports/sendfile.txt");
        byte[] content = "embedded-sendfile".getBytes(StandardCharsets.UTF_8);
        context.getBean(FileStorage.class).put(new UploadRequest(key, new ByteArrayInputStream(content),
                content.length, "text/plain"));
        String identifier = context.getBean(StableFileAccessService.class).identifier(key)
                .substring(STABLE_ID_VERSION_PREFIX_LENGTH);

        HttpResponse<String> complete = request(identifier, null);
        SendfileAttributes completeAttributes = SENDFILE.get();

        assertEquals(200, complete.statusCode());
        assertEquals("embedded-sendfile", complete.body());
        assertNotNull(completeAttributes);
        assertEquals(0L, completeAttributes.start());
        assertEquals(content.length, completeAttributes.end());

        SENDFILE.set(null);
        HttpResponse<String> range = request(identifier, "bytes=1-4");
        SendfileAttributes rangeAttributes = SENDFILE.get();

        assertEquals(206, range.statusCode());
        assertEquals("mbed", range.body());
        assertEquals("bytes 1-4/17", range.headers().firstValue("Content-Range").orElseThrow());
        assertNotNull(rangeAttributes);
        assertEquals(1L, rangeAttributes.start());
        assertEquals(5L, rangeAttributes.end());
    }

    /** 启用容器压缩后，标准二进制对象仍应走 sendfile 且不得声明错误的内容编码。 */
    @Test
    public void shouldUseSendfileForBinaryObjectWhenCompressionIsEnabled() throws Exception {
        Path root = Files.createTempDirectory("dinghai-sendfile-binary-");
        context = start(root, true, true);
        byte[] content = "native-binary-response".getBytes(StandardCharsets.UTF_8);
        String identifier = putAndIdentifier("reports/payload.bin", content, "application/octet-stream");

        SENDFILE.set(null);
        HttpResponse<byte[]> response = requestBytes(identifier, "gzip");

        assertEquals(200, response.statusCode());
        assertTrue(context.getBean(ServerProperties.class).getCompression().getEnabled());
        assertArrayEquals(content, response.body());
        assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
        assertNotNull(SENDFILE.get());
    }

    /** 可压缩文本必须保留流式响应，不能因新优化而注册 sendfile。 */
    @Test
    public void shouldStreamTextObjectWhenCompressionIsEnabled() throws Exception {
        Path root = Files.createTempDirectory("dinghai-sendfile-text-");
        context = start(root, true, true);
        byte[] content = "compressible text response".repeat(32).getBytes(StandardCharsets.UTF_8);
        String identifier = putAndIdentifier("reports/payload.txt", content, "text/plain");

        SENDFILE.set(null);
        HttpResponse<byte[]> response = requestBytes(identifier, "gzip");

        assertEquals(200, response.statusCode());
        assertArrayEquals(content, response.body());
        assertNull(SENDFILE.get());
    }

    /** 宿主关闭优化后，二进制请求应回退至流式路径。 */
    @Test
    public void shouldStreamBinaryObjectWhenBinarySendfileOptimizationIsDisabled() throws Exception {
        Path root = Files.createTempDirectory("dinghai-sendfile-disabled-");
        context = start(root, true, false);
        byte[] content = "binary compression fallback".repeat(32).getBytes(StandardCharsets.UTF_8);
        String identifier = putAndIdentifier("reports/payload.bin", content, "application/octet-stream");

        SENDFILE.set(null);
        HttpResponse<byte[]> response = requestBytes(identifier, "gzip");

        assertEquals(200, response.statusCode());
        assertArrayEquals(content, response.body());
        assertNull(SENDFILE.get());
    }

    /**
     * 启动配置为明文且未压缩响应的内嵌 Tomcat。
     *
     * @param root Local 主存储目录
     * @return 已启动应用上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private ConfigurableApplicationContext start(Path root) {
        return start(root, false, true);
    }

    /**
     * 按指定容器压缩和二进制 sendfile 开关启动测试应用。
     *
     * @param root Local 主存储目录
     * @param compressionEnabled 是否启用 Tomcat 压缩配置
     * @param binarySendfileOptimizationEnabled 是否启用二进制 sendfile 优化
     * @return 已启动应用上下文
     */
    private ConfigurableApplicationContext start(Path root, boolean compressionEnabled,
                                                  boolean binarySendfileOptimizationEnabled) {
        return new SpringApplicationBuilder(EmbeddedTomcatApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "server.compression.enabled=" + compressionEnabled,
                        "server.compression.min-response-size=1B",
                        "server.compression.mime-types=text/plain,application/octet-stream",
                        "dinghai.file-storage.provider=local", "dinghai.file-storage.bucket=documents",
                        "dinghai.file-storage.local.root=" + root,
                        "dinghai.file-storage.security.signing-key=embedded-sendfile-signing-key",
                        "dinghai.file-storage.storage-alias=documents",
                        "dinghai.file-storage.security.stable-id-key=embedded-stable-file-id-test-key-material",
                        "dinghai.file-storage.web.sendfile-binary-content-types-enabled="
                                + binarySendfileOptimizationEnabled)
                .run();
    }

    /**
     * 写入测试对象并返回稳定读取标识。
     *
     * @param key 对象 key
     * @param content 对象字节
     * @param contentType provider 确认的 MIME 类型
     * @return 去除版本前缀的稳定读取标识
     */
    private String putAndIdentifier(String key, byte[] content, String contentType) {
        ObjectKey objectKey = ObjectKey.of(key);
        context.getBean(FileStorage.class).put(new UploadRequest(objectKey, new ByteArrayInputStream(content),
                content.length, contentType));
        return context.getBean(StableFileAccessService.class).identifier(objectKey)
                .substring(STABLE_ID_VERSION_PREFIX_LENGTH);
    }

    /**
     * 对稳定读取端点发起明文 HTTP 请求。
     *
     * @param identifier 稳定文件 ID
     * @param range 可选单区间请求头
     * @return HTTP 响应
     * @throws Exception 网络请求失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private HttpResponse<String> request(String identifier, String range) throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/files/read/v1/" + identifier)).timeout(Duration.ofSeconds(10));
        if (range != null) {
            builder.header("Range", range);
        }
        return HttpClient.newHttpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 发起携带 Accept-Encoding 的稳定读取请求并保留原始响应字节。
     *
     * @param identifier 稳定文件 ID
     * @param acceptEncoding 接受的内容编码
     * @return HTTP 二进制响应
     * @throws Exception 网络请求失败时抛出
     */
    private HttpResponse<byte[]> requestBytes(String identifier, String acceptEncoding) throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/files/read/v1/" + identifier))
                .timeout(Duration.ofSeconds(10)).header("Accept-Encoding", acceptEncoding).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /** 内嵌 Tomcat 验证专用的最小 Spring Boot 应用。 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class EmbeddedTomcatApplication {
        /**
         * 在 DispatcherServlet 返回后读取 Tomcat sendfile 请求属性。
         *
         * @return 记录 sendfile 属性的 Servlet Filter
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        @Bean
        Filter captureSendfileAttributes() {
            return new SendfileCaptureFilter();
        }
    }

    /** 捕获真实 Tomcat 请求中控制器注册的 sendfile 参数。 */
    static final class SendfileCaptureFilter implements Filter {
        /**
         * 在请求处理完成后提取 sendfile 属性，不改写响应。
         *
         * @param request Servlet 请求
         * @param response Servlet 响应
         * @param chain 过滤器链
         * @throws java.io.IOException 请求处理失败时抛出
         * @throws ServletException Servlet 处理失败时抛出
         * @author lvdaxianer@yeah.net
         * @date 2026-09-12
         */
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws java.io.IOException, ServletException {
            chain.doFilter(request, response);
            HttpServletRequest servletRequest = (HttpServletRequest) request;
            Object filename = servletRequest.getAttribute(TomcatSendfileTransfer.FILENAME);
            if (filename instanceof String) {
                SENDFILE.set(new SendfileAttributes((Long) servletRequest.getAttribute(TomcatSendfileTransfer.START),
                        (Long) servletRequest.getAttribute(TomcatSendfileTransfer.END)));
            }
        }
    }

    /** 真实请求的安全 sendfile 区间。 */
    private record SendfileAttributes(long start, long end) { }
}
