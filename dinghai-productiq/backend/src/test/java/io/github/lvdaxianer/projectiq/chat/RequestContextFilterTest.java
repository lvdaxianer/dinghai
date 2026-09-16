package io.github.lvdaxianer.projectiq.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 验证 HTTP 请求上下文会被注入且在请求结束后清理。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class RequestContextFilterTest {
    /** 验证请求头 ID 被写入 MDC 和响应头，且过滤器 finally 清理 MDC。 */
    @Test
    void setsAndClearsRequestContext() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "request-123");
        request.addHeader("X-Trace-Id", "trace-456");
        AtomicReference<String> captured = new AtomicReference<>();

        FilterChain chain = (servletRequest, servletResponse) ->
                captured.set(MDC.get("requestId") + ":" + MDC.get("traceId"));
        filter.doFilter(request, response, chain);

        assertThat(captured).hasValue("request-123:trace-456");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-456");
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    /** 验证含换行符的关联 ID 不会进入 MDC、响应头或 access 日志。 */
    @Test
    void replacesUnsafeCorrelationHeaders() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "request-123\r\nforged=true");
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> captured.set(MDC.get("requestId"));

        filter.doFilter(request, response, chain);

        assertThat(captured.get()).doesNotContain("\r", "\n");
        assertThat(response.getHeader("X-Request-Id")).doesNotContain("\r", "\n");
    }
}
