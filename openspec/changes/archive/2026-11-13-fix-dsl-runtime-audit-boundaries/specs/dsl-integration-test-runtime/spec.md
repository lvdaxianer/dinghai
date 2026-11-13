# Spec Delta

## MODIFIED Requirements

### Requirement: Test extensions enforce trusted data scope
测试应用 SHALL 通过代码注册示例计划扩展验证逻辑删除和租户范围；租户身份 SHALL 来自服务端可信上下文，不能由请求体覆盖，扩展生成的条件 SHALL 以参数化约束进入计划，真实 HTTP/JDBC 验收 SHALL 断言返回结果只包含当前可信租户且未逻辑删除的数据。

#### Scenario: Deleted and foreign-tenant rows are excluded
- **WHEN** 请求查询包含已删除行或其他租户行的数据
- **THEN** 真实数据库结果只包含当前可信租户且未逻辑删除的数据，并返回预期业务列和值

#### Scenario: Request identity cannot bypass scope
- **WHEN** 请求体携带不同的 userId、domainId、tenantId 或 dataSourceId
- **THEN** 服务端拒绝或忽略该字段，查询仍使用服务端构造的可信上下文且不切换后端
