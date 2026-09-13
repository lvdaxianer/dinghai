package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 验证 Tomcat sendfile 注册的可用条件。 */
public class TomcatSendfileTransferTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRegisterCompleteFileForPlainTomcatRequest() throws Exception {
        Path file = temporaryFolder.newFile("content.bin").toPath();
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        MockHttpServletRequest request = tomcatRequest();

        assertTrue(TomcatSendfileTransfer.register(request, file, 0L, 5L, "application/octet-stream"));
        assertEquals(file.toAbsolutePath().toString(),
                request.getAttribute(TomcatSendfileTransfer.FILENAME));
        assertEquals(0L, request.getAttribute(TomcatSendfileTransfer.START));
        assertEquals(5L, request.getAttribute(TomcatSendfileTransfer.END));
    }

    @Test
    public void shouldRegisterSingleRangeForPlainTomcatRequest() throws Exception {
        Path file = temporaryFolder.newFile("range.bin").toPath();
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        MockHttpServletRequest request = tomcatRequest();

        assertTrue(TomcatSendfileTransfer.register(request, file, 1L, 3L, "application/octet-stream"));
        assertEquals(1L, request.getAttribute(TomcatSendfileTransfer.START));
        assertEquals(4L, request.getAttribute(TomcatSendfileTransfer.END));
    }

    @Test
    public void shouldRejectSecureCompressedOrUnsupportedRequests() throws Exception {
        Path file = temporaryFolder.newFile("content.bin").toPath();
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        MockHttpServletRequest secure = tomcatRequest();
        secure.setSecure(true);
        MockHttpServletRequest compressed = tomcatRequest();
        compressed.addHeader("Accept-Encoding", "gzip");
        MockHttpServletRequest unsupported = new MockHttpServletRequest();

        assertFalse(TomcatSendfileTransfer.register(secure, file, 0L, 5L, "application/octet-stream"));
        assertFalse(TomcatSendfileTransfer.register(compressed, file, 0L, 5L, "text/plain"));
        assertFalse(TomcatSendfileTransfer.register(unsupported, file, 0L, 5L, "application/octet-stream"));
    }

    /** 范围结束位置溢出时必须回退，不能注册越界的 sendfile 请求属性。 */
    @Test
    public void shouldRejectOverflowingFileRange() throws Exception {
        Path file = temporaryFolder.newFile("overflow.bin").toPath();
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        MockHttpServletRequest request = tomcatRequest();

        assertFalse(TomcatSendfileTransfer.register(request, file, Long.MAX_VALUE, 1L, "application/octet-stream"));
        assertNull(request.getAttribute(TomcatSendfileTransfer.FILENAME));
    }

    /** 注册区间超过实际文件大小时必须走流式回退。 */
    @Test
    public void shouldRejectRangeOutsideFile() throws Exception {
        Path file = temporaryFolder.newFile("short.bin").toPath();
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        MockHttpServletRequest request = tomcatRequest();

        assertFalse(TomcatSendfileTransfer.register(request, file, 4L, 2L, "application/octet-stream"));
        assertNull(request.getAttribute(TomcatSendfileTransfer.FILENAME));
    }

    private MockHttpServletRequest tomcatRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TomcatSendfileTransfer.SUPPORTED, Boolean.TRUE);
        return request;
    }
}
