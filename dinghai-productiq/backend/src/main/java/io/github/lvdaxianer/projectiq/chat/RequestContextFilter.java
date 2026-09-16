package io.github.lvdaxianer.projectiq.chat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个 HTTP 请求建立 requestId/traceId MDC，并在 Web 线程复用前清理上下文。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Component("productiqRequestContextFilter")
public final class RequestContextFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestContextFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    /**
     * 注入请求关联 ID、输出响应头并记录不含正文的 access 摘要。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws IOException I/O 处理失败
     * @throws ServletException Servlet 处理失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = identifier(request.getHeader(REQUEST_ID_HEADER));
        String traceId = identifier(request.getHeader(TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.info("[HTTP] method={} path={} status={} elapsedMs={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), elapsedMillis(startedAt));
            MDC.clear();
        }
    }

    /**
     * 仅接受有界的关联标识；缺失或异常输入时使用新 UUID。
     *
     * @param supplied 客户端请求头值
     * @return 可安全写入日志和 MDC 的关联标识
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String identifier(String supplied) {
        // 缺失、超长或非白名单字符的值均不能进入日志关联上下文。
        if (supplied == null || supplied.isBlank() || supplied.length() > MAX_IDENTIFIER_LENGTH
                || !supplied.chars().allMatch(this::safeIdentifierCharacter)) {
            return UUID.randomUUID().toString();
        }
        return supplied;
    }

    /**
     * 判断关联 ID 字符是否属于不会造成日志或 HTTP 头注入的白名单。
     *
     * @param character Unicode 码点
     * @return 是否允许进入关联上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private boolean safeIdentifierCharacter(int character) {
        return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9' || character == '-' || character == '_'
                || character == '.' || character == ':';
    }

    /**
     * 将纳秒差转换为 access 日志中的毫秒。
     *
     * @param startedAt 请求开始时间
     * @return 已耗时毫秒
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
