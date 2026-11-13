# Backend Adapters

## Purpose

为已编译的执行计划提供隔离的后端转换和执行能力，使 MySQL/Doris 与 PostgreSQL 可以复用统一计划模型并返回一致的结果结构，而不允许 DSL 直接控制数据库语法。

## Requirements

### Requirement: Backend adapters compile only supported plans
每个 Data Adapter SHALL 声明其支持的计划类型和能力，并 SHALL 只将已验证且能力匹配的计划转换为目标系统命令；不支持的能力 SHALL 返回稳定错误，不得忽略、降级或执行未转换内容。

#### Scenario: Supported plan is compiled
- **WHEN** 计划类型和全部能力均在 Data Adapter 的声明范围内
- **THEN** Adapter 生成目标系统可执行的参数化命令

#### Scenario: Unsupported plan is rejected
- **WHEN** 计划包含目标 Data Adapter 不支持的操作或能力
- **THEN** Adapter 返回不支持能力错误，且不建立数据库执行

### Requirement: SQL backends use parameterized commands
关系型 Data Adapter SHALL 将计划中的值作为绑定参数传递给 JDBC 驱动；表名、字段名、别名和函数只能来自已解析的结构或能力白名单，不能由未校验字符串拼接生成。

#### Scenario: Values are bound through JDBC
- **WHEN** 计划包含用户提供的条件值或分页值
- **THEN** 编译结果使用占位符和类型化绑定参数，不把值拼接进 SQL 文本

#### Scenario: Unresolved identifier is rejected
- **WHEN** 计划包含未解析或不在允许范围内的表、字段或别名
- **THEN** 编译失败且不调用 JDBC 执行

### Requirement: MySQL adapter supports MySQL and Doris common protocol behavior
MySQL Data Adapter SHALL 使用 MySQL JDBC 协议执行，并 SHALL 默认只生成 MySQL 与 Doris MySQL 协议共同支持的语法；Doris SHALL 通过该 Adapter 接入，不得要求独立 Doris Data Adapter。

#### Scenario: MySQL endpoint executes a common plan
- **WHEN** 配置的 JDBC DataSource 指向 MySQL 且计划仅使用公共能力
- **THEN** MySQL Data Adapter 生成参数化命令并执行成功

#### Scenario: Doris MySQL endpoint reuses the adapter
- **WHEN** 配置的 JDBC DataSource 指向 Doris MySQL 协议端口且计划仅使用公共能力
- **THEN** 系统仍使用 MySQL Data Adapter，不创建或选择 Doris 专用 Adapter

#### Scenario: Vendor-specific syntax is not exposed by default
- **WHEN** 计划要求仅某一厂商支持的 SQL 特性
- **THEN** 公共能力配置拒绝该计划，不生成厂商特有 SQL

### Requirement: PostgreSQL uses an independent backend compiler
PostgreSQL Data Adapter SHALL 使用 PostgreSQL 专用编译和 JDBC 执行能力，并 SHALL 不在 PostgreSQL Adapter 缺失时回退到 MySQL Adapter。

#### Scenario: PostgreSQL plan executes through PostgreSQL adapter
- **WHEN** 配置选择 PostgreSQL 且计划符合 PostgreSQL 能力
- **THEN** 系统使用 PostgreSQL 编译器和 PostgreSQL DataSource 执行

#### Scenario: Missing PostgreSQL adapter fails explicitly
- **WHEN** 配置选择 PostgreSQL 但 PostgreSQL Data Adapter 未注册
- **THEN** 系统返回配置错误并拒绝启动或执行，不改用 MySQL Adapter

### Requirement: Results are mapped to a unified result model
SQL Data Adapter SHALL 根据编译阶段确定的输出列映射读取 JDBC 结果，并返回统一列描述、逻辑类型、行数据、行数和截断状态；每一行的值数量 SHALL 与输出列数量一致；统一结果 SHALL 提供按输出列名组织的对象化记录视图；映射失败 SHALL 被明确报告。

#### Scenario: JDBC rows are mapped by stable output labels
- **WHEN** 数据库返回带有稳定输出别名的结果集
- **THEN** Adapter 按列映射转换 JDBC 类型并返回统一 `QueryResult`，其 `records` 按列名映射每行值

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
