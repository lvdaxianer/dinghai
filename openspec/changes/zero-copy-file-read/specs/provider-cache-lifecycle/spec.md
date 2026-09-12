## ADDED Requirements

### Requirement: 主存储与远程临时缓存生命周期隔离

Local provider SHALL 将 `local.root / bucket` 作为持久主存储，默认不得创建二级缓存或定时清理器，任何缓存清理任务 MUST NOT 删除主存储文件。MinIO/OSS provider SHALL 使用独立 `cache.root` 保存异步落盘副本，并仅清理完整、超过空闲 TTL 且属于该缓存根的副本。

#### Scenario: Local 主存储不被清理

- **WHEN** provider 为 `local` 且对象位于 `local.root / bucket`
- **THEN** Starter 不创建远程缓存清理任务，午夜清理不会删除该对象

#### Scenario: MinIO 冷读异步落盘

- **WHEN** MinIO/OSS 对象大小不超过阈值且首次读取成功
- **THEN** 响应立即从远程流返回，同时异步写入独立 `cache.root`，成功后原子发布缓存副本

#### Scenario: 远程缓存过期清理

- **WHEN** 缓存副本 sidecar 的最后访问时间超过配置 TTL
- **THEN** 清理器删除副本及 sidecar，但不访问或删除 provider 主对象

#### Scenario: 缓存根目录冲突

- **WHEN** `cache.root` 与 `local.root / bucket` 相同或互相包含
- **THEN** 应用启动失败并报告明确配置错误
