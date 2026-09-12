# file-storage-cache-test Specification

## Purpose

定义文件存储 Starter 在异步缓存填充期间验证去重行为的确定性测试约束，避免测试结果依赖机器调度或本地 I/O 速度。

## Requirements

### Requirement: 缓存填充并发测试确定性

文件存储 Starter 的缓存测试 SHALL 在验证相同 key 的未命中请求仅提交一个异步填充任务时，显式保持首次填充任务处于执行状态。测试 MUST 不依赖线程调度速度或临时目录读写速度，并且在失败路径释放测试阻塞资源。

#### Scenario: 填充未完成时第二次读取

- **WHEN** 首次缓存未命中已启动填充任务且其 provider 流仍被测试替身阻塞
- **THEN** 第二次读取继续从 provider 直通，且不会提交第二个填充任务
