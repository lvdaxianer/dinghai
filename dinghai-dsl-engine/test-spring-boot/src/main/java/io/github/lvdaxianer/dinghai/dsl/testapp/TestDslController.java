package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DSL 引擎测试 HTTP 接口。仅接受结构化 DSL 请求，不接受 SQL、adapter 或身份字段。
 */
@RestController
@RequestMapping(path = "/api/test/dsl", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestDslController {

    private final DslQueryService service;

    /**
     * 创建 DSL 测试控制器。
     *
     * @param service DSL 查询服务
     */
    public TestDslController(DslQueryService service) {
        this.service = service;
    }

    /**
     * 执行 DSL 并返回统一查询结果。
     *
     * @param request DSL 请求
     * @param httpRequest HTTP 请求
     * @return 查询结果
     */
    @PostMapping(path = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<QueryResult> execute(
            @RequestBody DslRequest request, HttpServletRequest httpRequest) {
        String traceId = TraceIdSupport.resolve(httpRequest);
        return ApiResponse.success(service.execute(request, traceId), traceId);
    }

    /**
     * 只解析和校验 DSL，不访问数据库。
     *
     * @param request DSL 请求
     * @param httpRequest HTTP 请求
     * @return 计划摘要
     */
    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PlanSummary> validate(
            @RequestBody DslRequest request, HttpServletRequest httpRequest) {
        String traceId = TraceIdSupport.resolve(httpRequest);
        return ApiResponse.success(PlanSummary.from(service.compile(request, traceId)), traceId);
    }

    /**
     * 返回安全计划解释，不返回 SQL 或绑定参数值。
     *
     * @param request DSL 请求
     * @param httpRequest HTTP 请求
     * @return 计划摘要
     */
    @PostMapping(path = "/explain", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PlanSummary> explain(
            @RequestBody DslRequest request, HttpServletRequest httpRequest) {
        String traceId = TraceIdSupport.resolve(httpRequest);
        return ApiResponse.success(PlanSummary.from(service.compile(request, traceId)), traceId);
    }

    /**
     * 提供仅用于 HTTP 边界测试的未预期异常入口。
     *
     * @return 不返回
     */
    @GetMapping("/unexpected-test-failure")
    public ApiResponse<Void> unexpectedTestFailure() {
        throw new IllegalStateException("database-password-must-not-leak");
    }
}
