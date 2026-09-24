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
    /** ASCII 字符集的最大码点。 */
    private static final int ASCII_MAX_CODE_POINT = 0x7f;

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
        // 容器、响应、范围或路径资格不满足时保留现有流式读取。
        if (!Boolean.TRUE.equals(request.getAttribute(SUPPORTED)) || request.isSecure()
                || requiresStreamingForCompression(request, contentType, binaryContentTypesEnabled)
                || start < 0L || length < 0L
                || !isSendfilePathSafe(file)) {
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

    /**
     * 判断最终本地路径能否安全传递给 Tomcat 原生 sendfile。
     *
     * @param path 候选本地文件路径
     * @return 非空、无跟随链接的普通文件且绝对路径完全为 ASCII 时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-24
     */
    static boolean isSendfilePathSafe(Path path) {
        // 空路径或非普通文件不能交给 Tomcat 原生传输。
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return path.toAbsolutePath().toString().chars()
                .allMatch(character -> character <= ASCII_MAX_CODE_POINT);
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
