## MODIFIED Requirements

### Requirement: Integration responses are safe and diagnosable
测试接口 SHALL 返回统一结果或稳定错误码、消息和 traceId；执行成功响应 SHALL 在保留 `columns`、`rows`、`rowCount` 和 `truncated` 的同时返回按列名组织的 `records`；`records` 不得省略值为 `null` 的字段；响应和日志不得包含数据库密码、Token、原始敏感参数或完整 SQL，解释接口只能返回安全的计划摘要和适配器信息。

#### Scenario: Execute response exposes readable records
- **WHEN** 调用方通过已注册 DSL 成功执行查询
- **THEN** `/api/test/dsl/execute` 返回 `records`，每条记录按输出列名表达，同时保留兼容性的 `rows`

#### Scenario: Execute response preserves null fields
- **WHEN** 数据库输出列的值为 `NULL`
- **THEN** 响应中的 `records` 包含该列名且值为 JSON `null`，不因序列化而省略字段

#### Scenario: Explain does not expose executable SQL
- **WHEN** 调用安全解释接口
- **THEN** 响应只包含 DSL、计划类型、扩展摘要、参数数量和适配器能力等受控信息，不包含可直接执行的 SQL

#### Scenario: Invalid or unavailable backend is visible
- **WHEN** 数据库连接、适配器或 DSL 校验失败
- **THEN** 响应提供稳定错误码和 traceId，且不静默切换到其他数据库或执行器
