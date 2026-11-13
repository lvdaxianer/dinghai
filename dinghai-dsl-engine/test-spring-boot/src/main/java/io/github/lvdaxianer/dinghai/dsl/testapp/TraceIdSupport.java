package io.github.lvdaxianer.dinghai.dsl.testapp;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 解析并限制外部链路标识，避免日志关联字段被注入控制字符。
 */
public final class TraceIdSupport {

    /** 控制链路标识长度和字符集。 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    /** 请求属性名。 */
    public static final String REQUEST_ATTRIBUTE = TraceIdSupport.class.getName();

    private TraceIdSupport() {
    }

    /**
     * 读取合法链路标识，否则生成服务端标识。
     *
     * @param request HTTP 请求
     * @return 安全链路标识
     */
    public static String resolve(HttpServletRequest request) {
        String candidate = request.getHeader("X-Trace-Id");
        String traceId = candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        return traceId;
    }

    /**
     * 返回异常处理阶段使用的链路标识。
     *
     * @param request HTTP 请求
     * @return 链路标识
     */
    public static String fromRequest(HttpServletRequest request) {
        Object traceId = request.getAttribute(REQUEST_ATTRIBUTE);
        return traceId instanceof String value ? value : resolve(request);
    }
}
