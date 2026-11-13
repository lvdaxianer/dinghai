package io.github.lvdaxianer.dinghai.dsl.postgresql;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalColumnDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalPlanBuilder;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryPlan;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalTableDefinition;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendExecutionException;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.AdapterRegistryException;
import io.github.lvdaxianer.dinghai.dsl.spi.registry.DataAdapterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 PostgreSQL Data Adapter 的 JDBC 执行、限制、映射和严格选择行为。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class PostgresqlDataAdapterTest {

    private static final DslContext DSL_CONTEXT = new DslContext("trace-1", Map.of());
    private static final QueryContext QUERY_CONTEXT = new QueryContext("trace-1", "postgresql", Map.of());

    /**
     * 验证 PostgreSQL JDBC 参数绑定、只读连接、超时和稳定列映射。
     */
    @Test
    void executesPostgresqlCommandAndMapsRows() throws Exception {
        JdbcFixture fixture = fixture();
        when(fixture.resultSet().next()).thenReturn(true, false);
        when(fixture.resultSet().getObject("f_status")).thenReturn("OPEN");

        QueryResult result = adapter(fixture.dataSource()).execute(plan(), QUERY_CONTEXT);

        assertEquals("postgresql", adapter(fixture.dataSource()).type());
        assertEquals(1, result.rowCount());
        assertEquals("OPEN", result.rows().getFirst().getFirst());
        assertEquals("f_status", result.columns().getFirst().name());
        verify(fixture.connection()).setReadOnly(true);
        verify(fixture.statement()).setQueryTimeout(30);
        verify(fixture.statement()).setMaxRows(10_001);
        verify(fixture.statement()).setObject(1, "OPEN");
    }

    /**
     * 验证超过 PostgreSQL 执行行数边界时截断结果。
     */
    @Test
    void truncatesRowsAtPostgresqlExecutionLimit() throws Exception {
        JdbcFixture fixture = fixture();
        when(fixture.resultSet().next()).thenReturn(true, true, false);
        when(fixture.resultSet().getObject("f_status")).thenReturn("OPEN", "CLOSED");

        QueryResult result = new PostgresqlDataAdapter(
                fixture.dataSource(), new PostgresqlRelationalCompiler(), new PostgresqlExecutionLimits(30, 1))
                .execute(plan(), QUERY_CONTEXT);

        assertEquals(1, result.rowCount());
        assertEquals(true, result.truncated());
        verify(fixture.statement()).setMaxRows(2);
    }

    /**
     * 验证 JDBC 值无法映射到逻辑类型时显式报告错误。
     */
    @Test
    void reportsPostgresqlMappingFailure() throws Exception {
        JdbcFixture fixture = fixture();
        when(fixture.resultSet().next()).thenReturn(true);
        when(fixture.resultSet().getObject("f_id")).thenReturn("not-a-long");

        BackendExecutionException exception = assertThrows(
                BackendExecutionException.class,
                () -> adapter(fixture.dataSource()).execute(longPlan(), QUERY_CONTEXT));

        assertEquals("RESULT_MAPPING_FAILED", exception.code());
    }

    /**
     * 验证未注册 PostgreSQL 适配器时不会静默回退到 MySQL。
     */
    @Test
    void rejectsMissingPostgresqlAdapterWithoutFallback() {
        DataAdapterRegistry registry = new DataAdapterRegistry(List.of());

        AdapterRegistryException exception = assertThrows(
                AdapterRegistryException.class, () -> registry.resolve("postgresql"));

        assertEquals(AdapterRegistryException.DATA_ADAPTER_TYPE_NOT_FOUND, exception.code());
    }

    /**
     * 构造默认 PostgreSQL 数据适配器。
     *
     * @param dataSource 服务端数据源
     * @return PostgreSQL 数据适配器
     */
    private static DataAdapter adapter(DataSource dataSource) {
        return new PostgresqlDataAdapter(
                dataSource, new PostgresqlRelationalCompiler(), PostgresqlExecutionLimits.defaults());
    }

    /**
     * 构造包含字符串条件的查询计划。
     *
     * @return 查询计划
     */
    private static RelationalQueryPlan plan() {
        return buildPlan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.status")),
                "where", List.of(Map.of(
                        "field", "f.status",
                        "operator", "eq",
                        "parameter", "status",
                        "logicalType", "string")),
                "limit", 10), Map.of("status", "OPEN"));
    }

    /**
     * 构造包含长整型字段的查询计划。
     *
     * @return 查询计划
     */
    private static RelationalQueryPlan longPlan() {
        return buildPlan(Map.of(
                "from", Map.of("table", "fact", "alias", "f"),
                "select", List.of(Map.of("field", "f.id")),
                "limit", 10), Map.of());
    }

    /**
     * 使用测试 schema 策略构造计划。
     *
     * @param payload 查询载荷
     * @param parameters 参数值
     * @return 查询计划
     */
    private static RelationalQueryPlan buildPlan(Map<String, Object> payload, Map<String, Object> parameters) {
        RelationalSchemaPolicy policy = (logicalName, context) -> Optional.of(
                new RelationalTableDefinition("fact", "fact_table", Map.of(
                        "id", new RelationalColumnDefinition("id", "id", "long"),
                        "status", new RelationalColumnDefinition("status", "status", "string"))));
        return new RelationalPlanBuilder(policy).build(
                new DslRequest("relational-query", 1, payload, parameters), DSL_CONTEXT);
    }

    /**
     * 构造 mock JDBC 环境。
     *
     * @return JDBC 测试夹具
     */
    private static JdbcFixture fixture() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("f_status");
        return new JdbcFixture(dataSource, connection, statement, resultSet);
    }

    /**
     * JDBC 测试夹具。
     *
     * @param dataSource 数据源
     * @param connection 连接
     * @param statement 预编译语句
     * @param resultSet 结果集
     */
    private record JdbcFixture(
            DataSource dataSource, Connection connection, PreparedStatement statement, ResultSet resultSet) {
    }
}
