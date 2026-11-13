## Why

当前 DSL 执行接口返回的 `rows` 是按列顺序排列的数组，调用方必须同时读取 `columns` 才能理解每个值的含义；当结果包含空值时，可读性和字段对应关系更差。需要在不破坏已有数组结果契约的前提下，增加按输出列名组织的对象化记录。

## What Changes

- 为统一 `QueryResult` 增加从列描述和行值生成对象化记录的能力。
- HTTP `/api/test/dsl/execute` 在保留 `columns`、`rows`、`rowCount` 和 `truncated` 的同时增加 `records`。
- `records` 按输出列顺序建立字段，数据库空值保留为 JSON `null`，不省略字段。
- 对结果列数与每行值数量进行一致性校验，避免字段和值错位。
- 更新测试接口文档和真实 MySQL HTTP 验证。

## Capabilities

### Modified Capabilities

- `backend-adapters`: 统一结果模型增加可读对象记录视图，并约束列和值的映射一致性。
- `dsl-integration-test-runtime`: 执行接口增加兼容的 `records` 响应字段，并明确空值行为。

## Impact

- 代码：`dinghai-dsl-engine/core` 的 `QueryResult`，测试 Spring Boot 工程的执行响应 DTO 和控制器。
- 接口：新增响应字段，不删除或改变已有 `rows` 字段。
- 文档和测试：更新 DSL HTTP API 文档，增加核心模型、HTTP 序列化和真实数据库验证。
