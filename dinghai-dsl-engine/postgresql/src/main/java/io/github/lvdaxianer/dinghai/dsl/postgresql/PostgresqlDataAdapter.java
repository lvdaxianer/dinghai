package io.github.lvdaxianer.dinghai.dsl.postgresql;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendExecutionException;
import io.github.lvdaxianer.dinghai.dsl.spi.CompiledCommand;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;
import io.github.lvdaxianer.dinghai.dsl.spi.ParameterBinding;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 通过服务端 DataSource 执行 PostgreSQL 命令的数据适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class PostgresqlDataAdapter implements DataAdapter {

    private final DataSource dataSource;
    private final PostgresqlRelationalCompiler compiler;
    private final PostgresqlExecutionLimits limits;

    /**
     * 使用默认编译器和执行限制创建 PostgreSQL 数据适配器。
     *
     * @param dataSource 服务端数据源
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public PostgresqlDataAdapter(DataSource dataSource) {
        this(dataSource, new PostgresqlRelationalCompiler(), PostgresqlExecutionLimits.defaults());
    }

    /**
     * 创建可配置的 PostgreSQL 数据适配器。
     *
     * @param dataSource 服务端数据源
     * @param compiler PostgreSQL 编译器
     * @param limits 执行限制
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public PostgresqlDataAdapter(
            DataSource dataSource, PostgresqlRelationalCompiler compiler, PostgresqlExecutionLimits limits) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * 返回后端类型。
     *
     * @return postgresql
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public String type() {
        return "postgresql";
    }

    /**
     * 返回支持的计划类型。
     *
     * @return 关系查询计划类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public Set<String> supportedPlanTypes() {
        return Set.of("relational-query");
    }

    /**
     * 返回 PostgreSQL 能力。
     *
     * @return 后端能力
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public BackendCapabilities capabilities() {
        return PostgresqlCapabilityProfile.common();
    }

    /**
     * 编译并执行统一计划。
     *
     * @param plan 后端无关计划
     * @param context 查询上下文
     * @return 统一查询结果
     * @throws BackendExecutionException JDBC 或映射失败
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public QueryResult execute(ExecutionPlan plan, QueryContext context) {
        Objects.requireNonNull(context, "context");
        CompiledCommand command = compiler.compile(plan, context);
        try {
            return executeCommand(command);
        } catch (SQLException exception) {
            throw new BackendExecutionException(
                    "JDBC_EXECUTION_FAILED", "PostgreSQL query execution failed", exception);
        }
    }

    /**
     * 执行已编译命令。
     *
     * @param command 已编译命令
     * @return 统一查询结果
     * @throws SQLException JDBC 错误
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private QueryResult executeCommand(CompiledCommand command) throws SQLException {
        String sql = requireSql(command.commandBody());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            connection.setReadOnly(true);
            statement.setQueryTimeout(limits.queryTimeoutSeconds());
            statement.setMaxRows(statementMaxRows());
            bindParameters(statement, command.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapResult(resultSet, command.outputColumns());
            }
        }
    }

    /**
     * 绑定参数值。
     *
     * @param statement JDBC 预编译语句
     * @param parameters 参数列表
     * @throws SQLException JDBC 错误
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void bindParameters(PreparedStatement statement, List<ParameterBinding> parameters)
            throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index).value());
        }
    }

    /**
     * 映射 JDBC 结果集。
     *
     * @param resultSet JDBC 结果集
     * @param columns 输出列
     * @return 统一查询结果
     * @throws SQLException JDBC 错误
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private QueryResult mapResult(ResultSet resultSet, List<QueryColumn> columns) throws SQLException {
        validateMetadata(resultSet.getMetaData(), columns);
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (resultSet.next()) {
            if (rows.size() >= limits.maxRows()) {
                truncated = true;
                break;
            }
            rows.add(mapRow(resultSet, columns));
        }
        return new QueryResult(columns, rows, rows.size(), truncated);
    }

    /**
     * 校验 JDBC 列数量。
     *
     * @param metadata JDBC 元数据
     * @param columns 输出列
     * @throws SQLException JDBC 错误
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void validateMetadata(ResultSetMetaData metadata, List<QueryColumn> columns)
            throws SQLException {
        if (metadata.getColumnCount() != columns.size()) {
            throw new BackendExecutionException(
                    "RESULT_MAPPING_FAILED", "JDBC column count does not match the compiled plan", null);
        }
    }

    /**
     * 映射单行结果。
     *
     * @param resultSet JDBC 结果集
     * @param columns 输出列
     * @return 统一行
     * @throws SQLException JDBC 错误
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Object> mapRow(ResultSet resultSet, List<QueryColumn> columns) throws SQLException {
        List<Object> row = new ArrayList<>(columns.size());
        for (QueryColumn column : columns) {
            try {
                row.add(mapValue(resultSet.getObject(column.name()), column.logicalType()));
            } catch (RuntimeException exception) {
                throw new BackendExecutionException("RESULT_MAPPING_FAILED", "JDBC value mapping failed", exception);
            }
        }
        return row;
    }

    /**
     * 按逻辑类型转换 JDBC 值。
     *
     * @param value JDBC 值
     * @param logicalType 逻辑类型
     * @return 映射值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Object mapValue(Object value, String logicalType) {
        if (value == null) {
            return null;
        }
        return switch (logicalType) {
            case "string" -> value instanceof CharSequence ? value.toString() : failMapping();
            case "long" -> value instanceof Number number ? number.longValue() : failMapping();
            case "integer" -> value instanceof Number number ? number.intValue() : failMapping();
            case "decimal" -> value instanceof BigDecimal decimal
                    ? decimal : value instanceof Number number ? new BigDecimal(number.toString()) : failMapping();
            case "boolean" -> value instanceof Boolean ? value : failMapping();
            case "date" -> value instanceof Date date ? date.toLocalDate()
                    : value instanceof LocalDate ? value : failMapping();
            case "datetime" -> value instanceof Timestamp timestamp ? timestamp.toLocalDateTime()
                    : value instanceof LocalDateTime ? value : failMapping();
            default -> value;
        };
    }

    /**
     * 触发安全映射失败。
     *
     * @return 不返回
     * @throws IllegalArgumentException 映射类型不匹配
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Object failMapping() {
        throw new IllegalArgumentException("JDBC value type does not match logical type");
    }

    /**
     * 计算 JDBC 最大读取行数。
     *
     * @return 最大读取行数
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private int statementMaxRows() {
        return limits.maxRows() == Integer.MAX_VALUE ? Integer.MAX_VALUE : limits.maxRows() + 1;
    }

    /**
     * 校验 SQL 命令主体。
     *
     * @param commandBody 命令主体
     * @return SQL 文本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireSql(Object commandBody) {
        if (!(commandBody instanceof String sql) || sql.isBlank()) {
            // 适配器只执行编译器产出的命令，不接收请求中的原始 SQL。
            throw new BackendExecutionException("COMMAND_INVALID", "compiled command is not SQL", null);
        }
        return sql;
    }
}
