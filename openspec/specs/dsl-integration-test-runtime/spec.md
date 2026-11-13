# DSL Integration Test Runtime

## Purpose

为 DSL 引擎提供可重复运行的 Spring Boot 验证环境，通过真实数据库和真实 JDBC 驱动验证解析、计划扩展、后端语法转换、查询映射与 HTTP 契约，避免用 Mock JDBC 掩盖协议和类型差异。

## Requirements

### Requirement: The test runtime accepts only custom DSL requests
测试应用 SHALL 提供 DSL 执行、校验和安全解释接口，输入必须符合版本化 `DslRequest`；接口 SHALL 拒绝原始 SQL、未知 DSL 类型、未注册适配器和客户端指定的数据库适配器。

#### Scenario: Custom DSL is executed through the engine
- **WHEN** 调用方提交已注册版本的 DSL 请求
- **THEN** 测试应用通过公共引擎完成解析、扩展、后端适配和结果映射后返回统一结果

#### Scenario: Raw SQL is rejected
- **WHEN** 请求体包含 SQL 字符串或 SQL 片段字段
- **THEN** 接口返回稳定校验错误，不建立数据库查询

### Requirement: Real database integrations verify adapter boundaries
集成测试 SHALL 使用真实 MySQL、Doris MySQL 协议端口和 PostgreSQL 实例及对应 JDBC 驱动；Doris SHALL 复用 MySQL Data Adapter，PostgreSQL SHALL 使用独立 PostgreSQL Data Adapter，测试不得以 Mock JDBC 作为适配器验收依据。

#### Scenario: MySQL and Doris share the MySQL adapter
- **WHEN** 测试数据源分别指向 MySQL 和 Doris MySQL 协议端口
- **THEN** 两者均通过 MySQL Data Adapter 执行公共 SQL 能力，且不启用 Doris 专用适配器

#### Scenario: PostgreSQL uses its own adapter
- **WHEN** 测试数据源指向 PostgreSQL
- **THEN** 请求通过 PostgreSQL Data Adapter 编译、绑定和映射结果，不回退到 MySQL

### Requirement: Test extensions enforce trusted data scope
测试应用 SHALL 通过代码注册示例计划扩展验证逻辑删除和租户范围；租户身份 SHALL 来自服务端可信上下文，不能由请求体覆盖，扩展生成的条件 SHALL 以参数化约束进入计划。

#### Scenario: Deleted and foreign-tenant rows are excluded
- **WHEN** 请求查询包含已删除行或其他租户行的数据
- **THEN** 真实数据库结果只包含当前可信租户且未逻辑删除的数据

#### Scenario: Request identity cannot bypass scope
- **WHEN** 请求体携带不同的 userId 或 tenantId
- **THEN** 服务端忽略或拒绝该字段，查询仍使用服务端构造的可信上下文

### Requirement: Integration responses are safe and diagnosable
测试接口 SHALL 返回统一结果或稳定错误码、消息和 traceId；响应和日志不得包含数据库密码、Token、原始敏感参数或完整 SQL，解释接口只能返回安全的计划摘要和适配器信息。

#### Scenario: Explain does not expose executable SQL
- **WHEN** 调用安全解释接口
- **THEN** 响应只包含 DSL、计划类型、扩展摘要、参数数量和适配器能力等受控信息，不包含可直接执行的 SQL

#### Scenario: Invalid or unavailable backend is visible
- **WHEN** 数据库连接、适配器或 DSL 校验失败
- **THEN** 响应提供稳定错误码和 traceId，且不静默切换到其他数据库或执行器
