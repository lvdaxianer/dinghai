# Spec Delta

## ADDED Requirements

### Requirement: Extension execution remains bounded and observable
计划扩展 SHALL 共享既有计划深度、节点、JOIN、返回行数和执行时间边界，并在审计和指标中以受控标识记录扩展应用结果；扩展不得把敏感参数或完整计划内容写入日志。

#### Scenario: Extension-induced growth is rejected
- **WHEN** 扩展使计划超过节点、JOIN 或深度限制
- **THEN** 系统在获取数据库连接前拒绝请求并记录安全的限制结果

#### Scenario: Extension identity is included in safe telemetry
- **WHEN** 扩展成功应用或失败
- **THEN** 审计或指标可以记录扩展标识、DSL 类型、计划摘要和结果状态，但不记录密码、Token 或参数值
