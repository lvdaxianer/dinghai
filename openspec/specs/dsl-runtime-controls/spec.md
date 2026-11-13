# DSL Runtime Controls

## Purpose

为 DSL 编译和后端执行提供有界、可观测且不会泄露敏感信息的运行时控制，避免复杂请求、缓存异常或下游故障拖垮应用或绕过安全策略。

## Requirements

### Requirement: Plans and executions are bounded
系统 SHALL 在执行前校验输入大小、计划深度、节点数量、JOIN 数量、返回行数、并发量和执行超时；超出配置边界时 SHALL 在获取数据库连接前拒绝请求。

#### Scenario: Complex plan is rejected before JDBC
- **WHEN** 计划超过深度、JOIN 或节点数量限制
- **THEN** 系统返回资源限制错误且不获取数据库连接

#### Scenario: Execution timeout is enforced
- **WHEN** 数据库执行超过配置的超时时间
- **THEN** 系统终止或取消执行并返回稳定超时错误

### Requirement: Compilation caches are bounded and versioned
系统 MAY 缓存解析模型、规范化计划和编译命令模板，但缓存 SHALL 有容量和 TTL 限制，并 SHALL 以 DSL、Adapter、编译器、Schema、Policy 和能力版本作为失效依据；默认不得缓存查询结果。

#### Scenario: Compiled template cache is reused safely
- **WHEN** 相同版本和策略下出现相同规范化计划
- **THEN** 系统可以复用不含敏感参数值的编译模板，并仍执行当前请求的权限校验

#### Scenario: Version change invalidates cached plan
- **WHEN** DSL、编译器、Schema、Policy 或后端能力版本发生变化
- **THEN** 旧缓存不得继续作为有效编译结果使用

### Requirement: Runtime outcomes are observable without sensitive data
系统 SHALL 提供解析、校验、编译、缓存、执行、映射的指标和 Trace 关联，并 SHALL 仅记录受控摘要；日志和审计不得包含密码、Token、原始敏感参数或完整结果。

#### Scenario: Successful execution records safe telemetry
- **WHEN** 一次 DSL 请求成功完成
- **THEN** 指标或审计事件可以记录 DSL 类型、版本、计划摘要、适配器、耗时和行数，但不记录敏感参数值

#### Scenario: Failure preserves traceability
- **WHEN** 解析、编译、连接、执行或映射失败
- **THEN** 系统返回稳定错误码和 Trace 标识，并在内部记录脱敏后的诊断信息

### Requirement: Cache and backend failures have explicit behavior
缓存不可用时系统 SHALL 可以绕过缓存继续执行；数据库连接、编译器或执行器不可用时系统 SHALL 显式失败，不得执行未经编译的请求或静默切换后端。

#### Scenario: Cache outage bypasses cache
- **WHEN** 编译缓存读取或写入失败
- **THEN** 系统执行有界的重新解析或编译，并保持原有安全校验

#### Scenario: Backend outage fails closed
- **WHEN** 目标 DataSource 或执行器不可用
- **THEN** 系统返回后端不可用错误，不调用其他未配置的 Data Adapter
