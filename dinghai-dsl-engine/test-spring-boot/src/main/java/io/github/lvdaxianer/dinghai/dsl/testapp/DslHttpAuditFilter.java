package io.github.lvdaxianer.dinghai.dsl.testapp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 测试应用的统一 HTTP 完成审计边界。
 *
 * <p>日志只记录路由摘要、状态、耗时和安全链路标识，不读取请求体、查询参数或响应体。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
@Component
public class DslHttpAuditFilter extends OncePerRequestFilter {

    private static final System.Logger LOGGER =
            System.getLogger("io.github.lvdaxianer.dinghai.dsl.testapp.http");

    /**
     * 记录一次请求完成事件，并管理请求线程的 MDC 生命周期。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdSupport.resolve(request);
        Map<String, String> previous = MDC.getCopyOfContextMap();
        long started = System.nanoTime();
        try {
            MDC.put("traceId", traceId);
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            LOGGER.log(System.Logger.Level.INFO,
                    "http request completed method={0} path={1} status={2} durationMs={3} traceId={4}",
                    request.getMethod(), safePath(request.getRequestURI()), response.getStatus(),
                    durationMs, traceId);
            MDC.clear();
            if (previous != null) {
                MDC.setContextMap(previous);
            }
        }
    }

    /**
     * 限制日志路由长度并去除控制字符。
     *
     * @param path 请求路径
     * @return 安全路径
     */
    private static String safePath(String path) {
        if (path == null) {
            return "";
        }
        String sanitized = path.replaceAll("[\\x00-\\x1F\\x7F]", "?");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
