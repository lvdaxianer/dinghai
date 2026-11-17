## MODIFIED Requirements

### Requirement: SQL backends use parameterized commands
关系型 Data Adapter SHALL 将计划中的值作为绑定参数传递给 JDBC 驱动，并 SHALL 按计划中的逻辑类型执行类型化绑定；表名、字段名、别名和函数只能来自已解析的结构或能力白名单，不能由未校验字符串拼接生成。

#### Scenario: Values are bound through JDBC
- **WHEN** 计划包含用户提供的条件值或分页值
- **THEN** 编译结果使用占位符和类型化绑定参数，不把值拼接进 SQL 文本

#### Scenario: Temporal values are bound with their semantic type
- **WHEN** 计划包含 `date`、`local_datetime` 或 `instant` 参数
- **THEN** Data Adapter 使用对应的 JDBC 日期、时间或绝对时间绑定方式，不把原始请求字符串交给驱动猜测

#### Scenario: Unresolved identifier is rejected
- **WHEN** 计划包含未解析或不在允许范围内的表、字段或别名
- **THEN** 编译失败且不调用 JDBC 执行

### Requirement: Results are mapped to a unified result model
SQL Data Adapter SHALL 根据编译阶段确定的输出列映射读取 JDBC 结果，并返回统一列描述、逻辑类型、行数据、行数和截断状态；每一行的值数量 SHALL 与输出列数量一致；统一结果 SHALL 提供按输出列名组织的对象化记录视图；时间结果 SHALL 按逻辑类型转换为统一 Java 时间值；映射失败 SHALL 被明确报告。

#### Scenario: JDBC rows are mapped by stable output labels
- **WHEN** 数据库返回带有稳定输出别名的结果集
- **THEN** Adapter 按列映射转换 JDBC 类型并返回统一 `QueryResult`，其 `records` 按列名映射每行值

#### Scenario: Temporal columns are mapped by logical type
- **WHEN** MySQL/PostgreSQL 返回 `DATE`、无时区时间戳或带时区时间戳
- **THEN** Adapter 分别映射为 `date`、`local_datetime` 或 `instant`，不根据 JVM 默认时区隐式改变语义

#### Scenario: Null values retain their output fields
- **WHEN** 数据库某个输出列的值为 `NULL`
- **THEN** `rows` 在对应位置保留 `null`，`records` 保留对应字段且字段值为 `null`

#### Scenario: Inconsistent row width is rejected
- **WHEN** 结果行的值数量与输出列数量不一致
- **THEN** 结果构造失败并报告结果映射错误，不返回字段和值错位的记录

#### Scenario: Duplicate output labels remain readable
- **WHEN** 输出列存在重复名称
- **THEN** `records` 为后续重复字段使用确定性后缀，避免 Map 覆盖值；`columns` 和 `rows` 保持原始顺序和值

#### Scenario: JDBC mapping failure is visible
- **WHEN** 返回值无法转换为计划声明的逻辑类型
- **THEN** Adapter 返回结果映射错误并保留可诊断的安全上下文
