# Spec Delta

## Purpose

为异步删除任务提供可检索、可关联且不泄露敏感信息的生命周期日志，支持通过 task id 和 trace id 定位登记、执行和失败恢复过程。

## ADDED Requirements

### Requirement: 异步删除生命周期日志

异步删除组件 SHALL 为任务登记、幂等命中、Worker 启停、任务开始、成功、重试和最终失败记录稳定的业务日志。任务级日志 MUST 包含 task id；涉及执行的日志 MUST 包含尝试次数和耗时或明确的执行阶段。

#### Scenario: 登记和幂等命中可定位
- **WHEN** 调用方首次登记或重复提交同一个幂等请求
- **THEN** 组件分别记录新登记或幂等命中事件，并包含返回的 task id，但不记录原始对象 key 或完整幂等键

#### Scenario: 删除成功可定位
- **WHEN** Worker 删除对象成功或确认对象不存在
- **THEN** 组件记录成功事件、task id、尝试次数、结果类型和耗时

#### Scenario: 删除失败可定位
- **WHEN** provider 删除发生可恢复失败或达到最大尝试次数
- **THEN** 组件分别记录重试或最终失败事件，并包含 task id、尝试次数、错误类型和安全的调度/耗时信息

### Requirement: 异步删除日志上下文安全

异步 Worker SHALL 为每次实际执行生成独立 trace id，并通过 MDC/TaskDecorator 传播和清理。日志 MUST NOT 包含原始 object key、幂等键、provider 异常消息、凭据或完整敏感异常堆栈。

#### Scenario: Worker 日志携带独立 trace
- **WHEN** Worker 从持久化任务表领取任务并执行 provider 删除
- **THEN** 任务生命周期日志可以通过本次执行的 trace id 关联，且任务完成后线程不会残留该 trace id

#### Scenario: 安全错误日志
- **WHEN** provider 抛出包含敏感信息的异常
- **THEN** 日志和任务失败记录仅保留安全错误类型，不输出异常原始消息或敏感对象路径
