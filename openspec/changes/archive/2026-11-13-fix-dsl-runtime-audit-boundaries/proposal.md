# Proposal

## Why

代码审查确认 DSL 引擎没有原始 SQL 执行入口，但 HTTP 审计、未预期异常诊断、参数规模边界和真实结果断言仍不完整。继续交付会让调用方只能看到安全错误，却无法在服务端可靠定位问题，也无法充分证明租户范围和参数约束在真实数据库上生效。

## What Changes

- 增加统一 HTTP trace/MDC 生命周期和完成审计日志，记录方法、路径、状态、耗时和 traceId，不记录请求体、SQL 或参数值。
- 让未预期异常只向客户端返回固定错误，同时在内部记录安全诊断；已知异常不再直接透传潜在驱动或 SQL 消息。
- 校验关系参数声明类型必须匹配白名单字段类型，并限制参数总量和 `IN` 元素数量。
- 明确 `dataSourceId` 是服务端审计/权限上下文，不接受客户端覆盖；实际后端仍由启动配置选择。
- 为 DSL 执行线程池增加可插拔上下文传播，测试应用传播 traceId 并验证任务结束后清理。
- 补充 HTTP 请求边界、数据域隔离和真实 MySQL、Doris、PostgreSQL 结果断言。

## Capabilities

### New Capabilities

- `dsl-observability-boundaries`: DSL HTTP 审计、错误诊断和异步 trace 上下文边界。

### Modified Capabilities

- `dsl-compilation`: 关系参数类型与参数规模必须在编译前校验。
- `dsl-integration-test-runtime`: 测试应用需要证明身份字段不能覆盖且真实结果受数据域约束。

## Impact

- 影响 `dinghai-dsl-engine` 的 SPI、relational、Spring Boot starter 和 test-spring-boot 模块。
- 不新增企业身份实现，不新增数据库凭据传递，不改变 MySQL/Doris 与 PostgreSQL 的适配器边界。
- 更新 DSL API 文档、配置元数据和 OpenSpec 主规格。
