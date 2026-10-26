# Design

## 日志事件

异步删除日志以稳定的中文业务前缀和英文事件名输出，所有事件携带 `taskId`；登记日志额外携带 `storageAlias`，Worker 日志携带 `attempt` 和 `workerId`。

| 事件 | 级别 | 字段 |
| --- | --- | --- |
| 任务新登记 | INFO | `taskId`, `storageAlias`, `idempotent=false` |
| 幂等命中 | INFO | `taskId`, `storageAlias`, `idempotent=true` |
| Worker 启动/停止 | INFO | `workerId`, `workerCount`, `queueCapacity` |
| 任务开始执行 | INFO | `taskId`, `attempt`, `workerId` |
| 删除成功 | INFO | `taskId`, `attempt`, `result=deleted/missing`, `durationMs` |
| 可恢复失败 | WARN | `taskId`, `attempt`, `nextRunAt`, `errorType`, `durationMs` |
| 最终失败 | ERROR | `taskId`, `attempt`, `errorType`, `durationMs` |
| Worker 轮询异常 | WARN | `errorType` |

轮询无任务、状态查询和单纯的 Repository 查询不逐条记录，避免空轮询和高频查询造成日志噪音。成功日志是任务级业务里程碑；如果宿主吞吐量很高，可按类或包调整日志级别，并通过 Metrics 观察成功率、延迟和队列深度。

## 安全边界

- 不记录原始 `objectKey`、`idempotencyKey`、provider 异常消息、完整 URL、凭据或完整异常堆栈。
- 失败日志记录异常简单类名作为 `errorType`，持久化的 `lastError` 保持现有安全摘要约束。
- 日志消息使用参数占位符，不进行字符串拼接。

## Trace 与 MDC

Worker 每次实际领取任务时生成一个新的 trace id，并放入当前线程 MDC；任务执行完成后恢复并清理原有上下文。现有 `TaskDecorator` 仍负责提交线程上下文快照，避免线程池复用产生串号。登记日志沿用调用方已有 MDC；没有上游 trace id 时不伪造业务字段，由日志框架保持统一策略。

## 验证

- 使用日志捕获器验证登记、幂等命中、成功、对象不存在、重试和最终失败事件包含 task id 与安全字段。
- 验证日志中不出现 object key、幂等键和 provider 异常消息。
- 验证异步任务日志带本次执行生成的 trace id，且执行后不会污染线程 MDC。
- 运行文件存储 Starter 测试、完整 Maven 测试和 OpenSpec 严格校验。
