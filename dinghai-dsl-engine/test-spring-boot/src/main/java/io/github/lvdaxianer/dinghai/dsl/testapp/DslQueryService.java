package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.QueryExecutionEngine;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 将 HTTP 请求转换为服务端可信上下文后交给通用 DSL 引擎。
 */
@Service
public class DslQueryService {

    private final QueryExecutionEngine engine;
    private final TestQueryProperties properties;

    /**
     * 创建测试查询服务。
     *
     * @param engine 通用 DSL 引擎
     * @param properties 测试应用可信配置
     */
    public DslQueryService(QueryExecutionEngine engine, TestQueryProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    /**
     * 执行查询。请求不能覆盖 adapter、数据源或身份上下文。
     *
     * @param request 结构化 DSL 请求
     * @param traceId 链路标识
     * @return 统一查询结果
     */
    public QueryResult execute(DslRequest request, String traceId) {
        return engine.execute(request, dslContext(traceId), queryContext(traceId));
    }

    /**
     * 仅编译计划，不获取数据库连接。
     *
     * @param request 结构化 DSL 请求
     * @param traceId 链路标识
     * @return 最终计划
     */
    public ExecutionPlan compile(DslRequest request, String traceId) {
        return engine.compile(request, dslContext(traceId), queryContext(traceId));
    }

    private DslContext dslContext(String traceId) {
        return new DslContext(traceId, trustedAttributes());
    }

    private QueryContext queryContext(String traceId) {
        return new QueryContext(traceId, properties.getDataSourceId(), trustedAttributes());
    }

    private Map<String, String> trustedAttributes() {
        return Map.of("userId", properties.getUserId(), "domainId", properties.getDomainId());
    }
}
