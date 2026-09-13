package io.github.lvdaxianer.dinghai.filestorage.spring;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 将符合条件的本地文件注册给 Tomcat 内核 sendfile。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
final class TomcatSendfileTransfer {
    static final String SUPPORTED = "org.apache.tomcat.sendfile.support";
    static final String FILENAME = "org.apache.tomcat.sendfile.filename";
    static final String START = "org.apache.tomcat.sendfile.start";
    static final String END = "org.apache.tomcat.sendfile.end";

    private TomcatSendfileTransfer() { }

    static boolean register(HttpServletRequest request, Path file, long start, long length) {
        return register(request, file, start, length, null, true);
    }

    /**
     * 为满足条件的本地文件注册 Tomcat sendfile 属性。
     *
     * @param request 当前 Servlet 请求
     * @param file 本地普通文件
     * @param start 传输起始字节
     * @param length 传输长度
     * @param contentType provider 确认的原始 MIME 类型
     * @param binaryContentTypesEnabled 标准二进制 sendfile 优化开关
     * @return 注册成功时返回 true
     */
    static boolean register(HttpServletRequest request, Path file, long start, long length,
                            String contentType, boolean binaryContentTypesEnabled) {
        if (!Boolean.TRUE.equals(request.getAttribute(SUPPORTED)) || request.isSecure()
                || requiresStreamingForCompression(request, contentType, binaryContentTypesEnabled)
                || start < 0L || length < 0L
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        final long end;
        try {
            end = Math.addExact(start, length);
        } catch (ArithmeticException exception) {
            return false;
        }
        try {
            if (end > Files.size(file)) return false;
        } catch (java.io.IOException exception) {
            return false;
        }
        request.setAttribute(FILENAME, file.toAbsolutePath().toString());
        request.setAttribute(START, start);
        request.setAttribute(END, end);
        return true;
    }

    private static boolean requiresStreamingForCompression(HttpServletRequest request, String contentType,
                                                           boolean binaryContentTypesEnabled) {
        return acceptsCompression(request) && (!binaryContentTypesEnabled
                || !SendfileContentTypes.isEligible(contentType));
    }

    private static boolean acceptsCompression(HttpServletRequest request) {
        String acceptEncoding = request.getHeader("Accept-Encoding");
        return acceptEncoding != null && !acceptEncoding.isBlank()
                && !acceptEncoding.trim().equalsIgnoreCase("identity");
    }
}
