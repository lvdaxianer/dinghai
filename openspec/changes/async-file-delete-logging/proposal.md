# Proposal

## Why

异步删除任务已经具备持久化、重试和租约接管能力，但当前日志只覆盖最终失败和轮询异常，无法快速定位任务登记、执行、成功、重试及耗时。补充受控业务日志可以让运维通过 task id 重建一次删除任务的生命周期，同时避免把对象路径、幂等键或 provider 异常消息写入日志。

## What Changes

- 为异步删除登记、Worker 启停、任务开始、成功、重试和最终失败增加结构稳定的业务日志。
- 为每次异步 Worker 执行生成独立 trace id，并沿用现有 MDC/TaskDecorator 透传和清理机制。
- 日志只记录 task id、storage alias、attempt、worker、结果、错误类型和耗时等安全摘要。
- 增加日志行为测试，确认敏感字段和 provider 异常消息不会进入日志内容。

## Capabilities

### New Capabilities

- `file-storage-async-delete-observability`: 异步删除任务的可检索生命周期日志、trace id 和安全字段约束。

### Modified Capabilities

无。

## Impact

- 影响 `dinghai-file-storage-spring-boot-starter` 的异步删除 Service 和 Worker。
- 不改变同步删除、任务状态、重试策略或对外 HTTP API。
- 不增加外部依赖；继续使用现有 SLF4J、MDC 和 TaskDecorator。
