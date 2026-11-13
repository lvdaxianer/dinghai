# dsl-observability-boundaries Specification

## Purpose
为 DSL HTTP 接口和异步执行链路提供可关联、可诊断且不泄露敏感数据的审计边界，确保安全错误响应与内部故障诊断同时成立。

## Requirements

### Requirement: HTTP requests have one safe completion audit
DSL HTTP 入口 SHALL 为每个请求生成或校验安全 traceId，并在请求结束时记录一次受控摘要；摘要 SHALL 包含方法、路径、状态、耗时和 traceId，不得包含请求体、SQL、绑定参数、密码或 Token。

#### Scenario: Successful request is auditable
- **WHEN** DSL 校验、解释或执行接口正常返回
- **THEN** 内部日志包含安全请求完成摘要和同一 traceId，Controller 不重复输出完整请求日志

#### Scenario: Failed request is auditable
- **WHEN** DSL 请求被拒绝或后端失败
- **THEN** 内部日志包含状态、耗时和 traceId，响应只包含稳定错误码、安全消息和 traceId

### Requirement: Error diagnostics are safe and retained internally
未预期异常 SHALL 记录不含敏感原文的内部诊断信息，并向调用方返回固定错误消息；已知后端、扩展、权限和限制异常 SHALL 不直接透传驱动、SQL、凭据或扩展内部消息。

#### Scenario: Unexpected failure keeps a safe diagnostic
- **WHEN** 处理器收到未分类运行时异常
- **THEN** 服务端记录异常类型和可关联诊断，客户端只收到 `INTERNAL_ERROR` 及 traceId

#### Scenario: Backend failure is mapped
- **WHEN** JDBC 或后端适配器失败
- **THEN** 客户端收到稳定后端错误码和固定消息，不收到 JDBC 原始错误正文

### Requirement: Async execution propagates and clears trace context
DSL 执行线程池 SHALL 使用可替换的上下文装饰器传播服务端 trace 上下文，并在任务结束后恢复或清理工作线程上下文，禁止跨请求串联 traceId。

#### Scenario: Worker sees submitted trace context
- **WHEN** DSL 请求提交后端执行任务
- **THEN** 任务内可观察到提交请求的 traceId，任务完成后工作线程不保留该 traceId
