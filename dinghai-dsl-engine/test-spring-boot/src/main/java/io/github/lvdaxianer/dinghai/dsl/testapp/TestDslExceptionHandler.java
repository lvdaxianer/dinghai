package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslCompilationException;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendExecutionException;
import io.github.lvdaxianer.dinghai.dsl.spi.ExecutionLimitException;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionException;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.AdapterRegistryException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 测试接口的稳定错误码和脱敏错误响应处理器。
 */
@RestControllerAdvice(assignableTypes = TestDslController.class)
public class TestDslExceptionHandler {

    private static final System.Logger LOGGER =
            System.getLogger("io.github.lvdaxianer.dinghai.dsl.testapp.errors");

    /**
     * 处理 DSL 解析和语义校验错误。
     *
     * @param exception 编译异常
     * @param request HTTP 请求
     * @return 400 错误
     */
    @ExceptionHandler(DslCompilationException.class)
    public ResponseEntity<ApiErrorResponse> handleCompilation(
            DslCompilationException exception, HttpServletRequest request) {
        var diagnostic = exception.diagnostics().getFirst();
        return error(HttpStatus.BAD_REQUEST, diagnostic.code(), diagnostic.message(),
                List.of(new ApiErrorResponse.ErrorDetail(diagnostic.path(), diagnostic.message())), request);
    }

    /**
     * 处理请求 JSON 结构错误。
     *
     * @param exception 请求解析异常
     * @param request HTTP 请求
     * @return 400 错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "request body is invalid", List.of(), request);
    }

    /**
     * 处理后端执行错误。
     *
     * @param exception 后端异常
     * @param request HTTP 请求
     * @return 502 错误
     */
    @ExceptionHandler(BackendExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleBackend(
            BackendExecutionException exception, HttpServletRequest request) {
        logExpectedFailure(exception, request);
        return error(HttpStatus.BAD_GATEWAY, exception.code(), "query backend execution failed", List.of(), request);
    }

    /**
     * 处理运行时边界错误。
     *
     * @param exception 执行限制异常
     * @param request HTTP 请求
     * @return 429 错误
     */
    @ExceptionHandler(ExecutionLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleLimit(
            ExecutionLimitException exception, HttpServletRequest request) {
        logExpectedFailure(exception, request);
        return error(HttpStatus.TOO_MANY_REQUESTS, exception.code(), "query execution limit exceeded", List.of(), request);
    }

    /**
     * 处理计划扩展错误。
     *
     * @param exception 扩展异常
     * @param request HTTP 请求
     * @return 400 错误
     */
    @ExceptionHandler(PlanExtensionException.class)
    public ResponseEntity<ApiErrorResponse> handleExtension(
            PlanExtensionException exception, HttpServletRequest request) {
        logExpectedFailure(exception, request);
        return error(HttpStatus.BAD_REQUEST, exception.code(), "query plan extension rejected", List.of(), request);
    }

    /**
     * 处理适配器注册错误。
     *
     * @param exception 注册异常
     * @param request HTTP 请求
     * @return 400 错误
     */
    @ExceptionHandler(AdapterRegistryException.class)
    public ResponseEntity<ApiErrorResponse> handleAdapter(
            AdapterRegistryException exception, HttpServletRequest request) {
        logExpectedFailure(exception, request);
        return error(HttpStatus.BAD_REQUEST, exception.code(), "configured data adapter is unavailable", List.of(), request);
    }

    /**
     * 处理测试应用的可信上下文错误。
     *
     * @param exception 权限异常
     * @param request HTTP 请求
     * @return 403 错误
     */
    @ExceptionHandler(TestAuthorizationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorization(
            TestAuthorizationException exception, HttpServletRequest request) {
        logExpectedFailure(exception, request);
        return error(HttpStatus.FORBIDDEN, exception.code(), "query data scope is not authorized", List.of(), request);
    }

    /**
     * 处理其他请求参数错误。
     *
     * @param exception 参数异常
     * @param request HTTP 请求
     * @return 400 错误
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "request is invalid", List.of(), request);
    }

    /**
     * 处理未预期异常，避免向调用方暴露堆栈、SQL 或驱动信息。
     *
     * @param exception 未预期异常
     * @param request HTTP 请求
     * @return 500 错误
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String traceId = TraceIdSupport.fromRequest(request);
        LOGGER.log(System.Logger.Level.ERROR,
                "unexpected DSL HTTP failure traceId={0} exceptionType={1} stack={2}",
                traceId, exception.getClass().getName(), stackSummary(exception));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal DSL service error", List.of(), request);
    }

    /**
     * 记录可预期错误的安全摘要，不记录异常消息、请求体或参数值。
     *
     * @param exception 业务异常
     * @param request HTTP 请求
     */
    private static void logExpectedFailure(RuntimeException exception, HttpServletRequest request) {
        LOGGER.log(System.Logger.Level.WARNING,
                "expected DSL HTTP failure traceId={0} code={1} exceptionType={2}",
                TraceIdSupport.fromRequest(request), errorCode(exception), exception.getClass().getName());
    }

    /**
     * 提取有限长度的堆栈摘要，避免异常消息中的敏感信息进入日志。
     *
     * @param exception 未预期异常
     * @return 堆栈摘要
     */
    private static String stackSummary(Exception exception) {
        return java.util.Arrays.stream(exception.getStackTrace())
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
    }

    /**
     * 读取稳定错误码，不读取异常消息。
     *
     * @param exception 业务异常
     * @return 稳定错误码
     */
    private static String errorCode(RuntimeException exception) {
        if (exception instanceof BackendExecutionException value) {
            return value.code();
        }
        if (exception instanceof ExecutionLimitException value) {
            return value.code();
        }
        if (exception instanceof PlanExtensionException value) {
            return value.code();
        }
        if (exception instanceof AdapterRegistryException value) {
            return value.code();
        }
        if (exception instanceof TestAuthorizationException value) {
            return value.code();
        }
        return "REQUEST_INVALID";
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.ErrorDetail> details,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code, message, details, TraceIdSupport.fromRequest(request)));
    }
}
