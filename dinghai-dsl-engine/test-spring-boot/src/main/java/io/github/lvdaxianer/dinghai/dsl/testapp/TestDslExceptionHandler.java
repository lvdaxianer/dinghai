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

/**
 * 测试接口的稳定错误码和脱敏错误响应处理器。
 */
@RestControllerAdvice(assignableTypes = TestDslController.class)
public class TestDslExceptionHandler {

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
        return error(HttpStatus.BAD_GATEWAY, exception.code(), exception.getMessage(), List.of(), request);
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
        return error(HttpStatus.TOO_MANY_REQUESTS, exception.code(), exception.getMessage(), List.of(), request);
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
        return error(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), List.of(), request);
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
        return error(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), List.of(), request);
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
        return error(HttpStatus.FORBIDDEN, exception.code(), exception.getMessage(), List.of(), request);
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
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal DSL service error", List.of(), request);
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
