# Tasks

## 1. 可选任务存储与配置

- [x] 1.1 增加删除模式、数据库类型、SQLite 路径、租约、轮询、重试和线程池边界配置，并验证默认同步模式不创建数据库相关 Bean 或要求 JDBC 配置
- [x] 1.2 增加删除任务状态模型、Repository 接口、SQLite 与宿主 DataSource 持久化实现和幂等 schema 初始化，并通过 SQLite 持久化/重启测试验证任务可恢复
- [x] 1.3 增加条件更新抢占、lease token 和安全状态更新测试，验证多 worker 竞争时只有一个实例获得任务且过期租约可被接管

## 2. 异步删除服务与 Worker

- [x] 2.1 增加异步删除提交与状态查询服务，验证合法登记、重复幂等、缺失任务和未配置异步能力时的同步兼容行为
- [x] 2.2 增加独立有界删除执行器和轮询 Worker，验证成功、对象不存在、可恢复失败重试、最大重试次数和安全失败记录
- [x] 2.3 验证服务重启恢复、租约超时接管、provider 删除超时后的幂等重试，以及删除执行器与 Web 读取执行器隔离

## 3. 使用说明与集成验证

- [ ] 3.1 更新文件存储中英文 README 与配置示例，说明 sync、SQLite 单机和宿主 DataSource 高可用模式，并验证文档配置键与实现一致
- [ ] 3.2 运行 OpenSpec 严格校验、文件存储 Starter 聚焦测试和完整 Maven 验证，确认现有同步删除/provider 回归测试不受影响

## Workflow follow-up

- 完成每个任务的 RED、GREEN、 broader verification、代码审查和 per-task audit 后，再标记对应复选框并提交。
- 全部任务完成后执行最终审计并归档 `async-file-delete` OpenSpec 变更。
